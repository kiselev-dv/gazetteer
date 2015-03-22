package me.osm.gazetteer.web.api;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import me.osm.gazetteer.web.ESNodeHodel;
import me.osm.gazetteer.web.Main;
import me.osm.gazetteer.web.api.imp.APIUtils;
import me.osm.gazetteer.web.api.imp.QToken;
import me.osm.gazetteer.web.api.imp.Query;
import me.osm.gazetteer.web.api.imp.QueryAnalyzer;
import me.osm.gazetteer.web.imp.IndexHolder;
import me.osm.gazetteer.web.imp.Replacer;
import me.osm.gazetteer.web.utils.OSMDocSinglton;
import me.osm.gazetteer.web.utils.ReplacersCompiler;
import me.osm.osmdoc.model.Feature;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.geo.GeoPoint;
import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.DisMaxQueryBuilder;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.OrFilterBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.sort.SortBuilders;
import org.json.JSONArray;
import org.json.JSONObject;
import org.restexpress.Request;
import org.restexpress.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SearchAPI {

	private static final String STRICT_SEARCH_HEADER = "strict";

	/**
	 * Explain search results or not 
	 * (<code>true<code> for explain)
	 * default is false
	 * */
	public static final String EXPLAIN_HEADER = "explain";
	
	/**
	 * Querry string
	 * */
	public static final String Q_HEADER = "q";
	
	/**
	 * Type of feature. [adrpnt, poipnt etc]
	 * */
	public static final String TYPE_HEADER = "type";

	/**
	 * Include or not object full geometry
	 * (<code>true<code> to include)
	 * default is false
	 * */
	public static final String FULL_GEOMETRY_HEADER = "full_geometry";

	/**
	 * Search inside given BBOX
	 * west, south, east, north'
	 * */
	public static String BBOX_HEADER = "bbox";
	
	/**
	 * Look for poi of exact types
	 * */
	public static final String POI_CLASS_HEADER = "poiclass";

	/**
	 * Look for poi of exact types (from hierarchy branch)
	 * */
	public static final String POI_GROUP_HEADER = "poigroup";
	
	/**
	 * Latitude of map center
	 * */
	public static final String LAT_HEADER = "lat";

	/**
	 * Longitude of map center
	 * */
	public static final String LON_HEADER = "lon";
	
	/**
	 * Features id's of higher objects to filter results.
	 * */
	public static final String REFERENCES_HEADER = "ref";
	
	/**
	 * Use it, if you have separate addresses parts texts, to search over.
	 * */
	public static final String PARTS_HEADER = "parts";
	
	protected List<Replacer> housenumberReplacers = new ArrayList<>();
	{
		ReplacersCompiler.compile(housenumberReplacers, new File("config/replacers/hnSearchReplacers"));
	}
	
	private static final Logger log = LoggerFactory.getLogger(SearchAPI.class);
	
	protected QueryAnalyzer queryAnalyzer = new QueryAnalyzer();
	
	public JSONObject read(Request request, Response response) throws IOException  {
		return read(request, response, false);
	}
	
	public JSONObject read(Request request, Response response, boolean resendedAfterFail) 
			throws IOException {

		try {
			boolean explain = "true".equals(request.getHeader(EXPLAIN_HEADER));
			String querryString = StringUtils.stripToNull(request.getHeader(Q_HEADER));
			
			BoolQueryBuilder q = null;
			
			Set<String> types = getSet(request, TYPE_HEADER);
			String hname = request.getHeader("hierarchy");
			
			Set<String> poiClass = getSet(request, POI_CLASS_HEADER);
			addPOIGroups(request, poiClass, hname);
			
			Double lat = getDoubleHeader(LAT_HEADER, request);
			Double lon = getDoubleHeader(LON_HEADER, request);
			
			Set<String> refs = getSet(request, REFERENCES_HEADER);
			
			if(querryString == null && poiClass.isEmpty() && types.isEmpty() && refs.isEmpty()) {
				return null;
			}
			
			Query query = queryAnalyzer.getQuery(querryString);
			
			List<JSONObject> poiType = null;
			if(query != null) {
				poiType = findPoiClass(query);
			}

			boolean strictRequested = request.getHeader(STRICT_SEARCH_HEADER) != null && Boolean.parseBoolean(request.getHeader(STRICT_SEARCH_HEADER));
			
			if(querryString != null) {
				if(resendedAfterFail && !strictRequested) {
					//not a strict query
					q = getSearchQuerry(query, false);
				}
				else {
					//strict query
					q = getSearchQuerry(query, true);
				}
			}
			else {
				q = QueryBuilders.boolQuery();
			}
			
			if(!types.isEmpty()) {
				q.must(QueryBuilders.termsQuery("type", types));
			}
			
			if(!poiClass.isEmpty()) {
				q.must(QueryBuilders.termsQuery("poi_class", poiClass));
			}
			
			QueryBuilder qb = poiClass.isEmpty() ? QueryBuilders.filteredQuery(q, getFilter(querryString)) : q;
			
			qb = rescore(qb, lat, lon, poiClass);
			
			List<String> bbox = getList(request, BBOX_HEADER);
			if(!bbox.isEmpty() && bbox.size() == 4) {
				qb = addBBOXRestriction(qb, bbox);
			}
			
			if(!refs.isEmpty()) {
				qb = addRefsRestriction(qb, refs);
			}
			
			Client client = ESNodeHodel.getClient();
			SearchRequestBuilder searchRequest = client
					.prepareSearch("gazetteer").setTypes(IndexHolder.LOCATION)
					.setQuery(qb)
					.setExplain(explain);
			
			searchRequest.addAggregation(AggregationBuilders.terms("highways").field("name"));
			
			searchRequest.addSort(SortBuilders.scoreSort());

			searchRequest.setFetchSource(true);
			
			APIUtils.applyPaging(request, searchRequest);
			
			SearchResponse searchResponse = searchRequest.execute().actionGet();
			
			if(searchResponse.getHits().getHits().length == 0) {
				if(Main.config().isReRestrict() && !strictRequested && !resendedAfterFail) {
					return read(request, response, true);
				}
			}
			
			boolean fullGeometry = request.getHeader(FULL_GEOMETRY_HEADER) != null 
					&& "true".equals(request.getParameter(FULL_GEOMETRY_HEADER));
			
			JSONObject answer = APIUtils.encodeSearchResult(
					searchResponse,	fullGeometry, explain);
			
			answer.put("request", request.getHeader(Q_HEADER));
			if(poiType != null && !poiType.isEmpty()) {
				answer.put("matched_type", new JSONArray(poiType));
			}
			
			answer.put("resended_after_fail", resendedAfterFail);
			
			APIUtils.resultPaging(request, answer);
			
			return answer;
		}
		catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
		
	}

	private QueryBuilder addRefsRestriction(QueryBuilder qb, Set<String> refs) {
		qb = QueryBuilders.filteredQuery(qb, FilterBuilders.termsFilter("refs", refs));
		return qb;
	}

	protected List<JSONObject> findPoiClass(Query query) {
		
		Client client = ESNodeHodel.getClient();
		
		String qs = query.required().woNumbers().toString();
		
		SearchRequestBuilder searchRequest = client.prepareSearch("gazetteer").setTypes(IndexHolder.POI_CLASS)
				.setQuery(QueryBuilders.multiMatchQuery(qs, "translated_title", "keywords"));
		
		SearchHit[] hits = searchRequest.get().getHits().getHits();

		List<JSONObject> result = new ArrayList<JSONObject>(hits.length);
		if(hits.length > 0) {
			result.add(new JSONObject(hits[0].sourceAsString()));
		}
		
		return result;
	}

	private Double getDoubleHeader(String header, Request request) {
		String valString = request.getHeader(header);
		if(valString != null) {
			try{
				return Double.parseDouble(valString);
			}
			catch (NumberFormatException e) {
				return null;
			}
		}
		
		return null;
	}

	private FilterBuilder getFilter(String querry) {
		
		// Мне нужны только те пои, для которых совпал name и/или тип.
		BoolQueryBuilder filterQ = QueryBuilders.boolQuery()
				.must(QueryBuilders.termQuery("type", "poipnt"))
				.must(QueryBuilders.multiMatchQuery(querry, "name.text", "poi_class", "poi_class_trans"));
		
		OrFilterBuilder orFilter = FilterBuilders.orFilter(
				FilterBuilders.queryFilter(filterQ), 
				FilterBuilders.notFilter(FilterBuilders.termsFilter("type", "poipnt")));
		
		return orFilter;
	}

	private QueryBuilder addBBOXRestriction(QueryBuilder qb, List<String> bbox) {
		qb = QueryBuilders.filteredQuery(qb, 
				FilterBuilders.geoBoundingBoxFilter("center_point")
				.bottomLeft(Double.parseDouble(bbox.get(1)), Double.parseDouble(bbox.get(0)))
				.topRight(Double.parseDouble(bbox.get(3)), Double.parseDouble(bbox.get(2))));
		return qb;
	}

	private static QueryBuilder rescore(QueryBuilder q, Double lat, Double lon, Set<String> poiClass) {
		
		FunctionScoreQueryBuilder qb = 
				QueryBuilders.functionScoreQuery(q)
					.scoreMode("avg")
					.boostMode(CombineFunction.REPLACE);
		
		if(lat != null && lon != null) {
			qb.add(ScoreFunctionBuilders.linearDecayFunction("center_point", 
					new GeoPoint(lat, lon), "5km").setWeight(poiClass.isEmpty() ? 5 : 25));
		}
		
		qb.add(ScoreFunctionBuilders.fieldValueFactorFunction("weight").setWeight(0.005f));
		
		qb.add(ScoreFunctionBuilders.scriptFunction("score", "expression").setWeight(1));
		
		return qb;
	}

	private void addPOIGroups(Request request, Set<String> poiClass, String hname) {
		for(String s : getSet(request, POI_GROUP_HEADER)) {
			Collection<? extends Feature> hierarcyBranch = OSMDocSinglton.get().getReader().getHierarcyBranch(hname, s);
			if(hierarcyBranch != null) {
				for(Feature f : hierarcyBranch) {
					poiClass.add(f.getName());
				}
			}
		}
	}

	private Set<String> getSet(Request request, String header) {
		Set<String> types = new HashSet<String>();
		List<String> t = request.getHeaders(header);
		if(t != null) {
			for(String s : t) {
				types.addAll(Arrays.asList(StringUtils.split(s, ", []\"\'")));
			}
		}
		return types;
	}

	private List<String> getList(Request request, String header) {
		List<String> result = new ArrayList<String>();
		List<String> t = request.getHeaders(header);
		if(t != null) {
			for(String s : t) {
				result.addAll(Arrays.asList(StringUtils.split(s, ", []\"\'")));
			}
		}
		return result;
	}

	public BoolQueryBuilder getSearchQuerry(Query query, boolean strict) {
		
		BoolQueryBuilder resultQuery = QueryBuilders.boolQuery();
		
		commonSearchQ(query, resultQuery, strict);
		
		resultQuery.disableCoord(true);
		resultQuery.mustNot(QueryBuilders.termQuery("weight", 0));
		
		return resultQuery;
		
	}

	public void commonSearchQ(Query query, BoolQueryBuilder resultQuery, boolean strict) {
		
		int numbers = query.countNumeric();
		
		List<String> nameExact = new ArrayList<String>();
		List<String> required = new ArrayList<String>();
		LinkedHashSet<String> nums = new LinkedHashSet<String>();
		
		Collection<String> housenumbers = fuzzyNumbers(query.toString());

		if(!housenumbers.isEmpty()) {
			
			BoolQueryBuilder numberQ = QueryBuilders.boolQuery();

			List<String> reversed = new ArrayList<>(housenumbers);
			Collections.sort(reversed, new Comparator<String>() {

				@Override
				public int compare(String o1, String o2) {
					return Integer.compare(StringUtils.length(o1), StringUtils.length(o2));
				}
				
			});
			Collections.reverse(reversed);
			
			int i = 1;
			for(String variant : reversed) {
				numberQ.should(QueryBuilders.termQuery("housenumber", variant).boost(i++ * 20)); 
				numberQ.should(QueryBuilders.matchQuery("search", variant).boost(i++ * 10));
			}
			
			resultQuery.must(numberQ.boost(10));
		}
		
		for(QToken token : query.listToken()) {
			//optional
			if(token.isOptional()) {
				resultQuery.should(QueryBuilders.matchQuery("search", token.toString()));
			}
			//number
			else if(token.isNumbersOnly()) {
				// Если реплейсеры не распознали номер дома
				if(housenumbers.isEmpty()) {
					if (numbers == 1) {
						resultQuery.must(QueryBuilders.matchQuery("search", token.toString())).boost(10);
					}
					else {
						resultQuery.should(QueryBuilders.matchQuery("search", token.toString())).boost(10);
					}
				}
			}
			// Если реплейсеры не распознали номер дома, то пробуем действовать по старинке.
			// Возможно стоит совместить оба метода
			else if(token.isHasNumbers()) {
				if(housenumbers.isEmpty()) {
					BoolQueryBuilder numberQ = QueryBuilders.boolQuery();
					numberQ.disableCoord(true);
					
					//for numbers in street names
					numberQ.should(QueryBuilders.matchQuery("search", token.toString()));
					
					numberQ.should(QueryBuilders.termQuery("housenumber", token.toString()).boost(5));
					
					nums.add(token.toString());
					
					resultQuery.must(numberQ);
					nameExact.add(token.toString());
				}
			}
			//regular token
			else {
				required.add(token.toString());
				nameExact.add(token.toString());
			}
			
			if (token.isHasNumbers()) {
				nums.add(token.toString());
			}
		}
		
		BoolQueryBuilder requiredQ = QueryBuilders.boolQuery();
		requiredQ.disableCoord(true);
		
		
		for(String t : required) {
			if(strict) {
				requiredQ.should(QueryBuilders.matchQuery("search", t).boost(20));
			}
			else {
				
				MatchQueryBuilder search = QueryBuilders.matchQuery("search", t).boost(20);
				MatchQueryBuilder nearestS = QueryBuilders.matchQuery("nearby_streets.name", t).boost(0.2f);
				
				requiredQ.should(QueryBuilders.disMaxQuery().add(search).add(nearestS).tieBreaker(0.4f));
			}
		}
		
		if(strict) {
			requiredQ.minimumNumberShouldMatch(required.size());
		}
		else {
			int r = required.size();
			if(r > 3) {
				requiredQ.minimumNumberShouldMatch(r - 2);
			}
			else if(r >= 2) {
				requiredQ.minimumNumberShouldMatch(r - 1);
			}
		}
		
		resultQuery.must(requiredQ);
		
		log.trace("Request: {} Required tokens: {}", query, required);
		log.trace("Request: {} Housenumbers variants: {}", query, housenumbers);
		
		List<String> cammel = new ArrayList<String>();
		for(String s : nameExact) {
			cammel.add(s);
			cammel.add(StringUtils.capitalize(s));
		}
		
		resultQuery.should(QueryBuilders.termsQuery("name.exact", cammel).boost(10));
		resultQuery.should(QueryBuilders.termsQuery("housenumber", nums).boost(250));
		
	}

	private Collection<String> fuzzyNumbers(String string) {

		List<String> result = new ArrayList<>();
		
		if(StringUtils.isNotBlank(string)) {
			LinkedHashSet<String> tr = transformHousenumbers(string);
			result.addAll(tr);
		}
		
		return result;
	}

	private LinkedHashSet<String> transformHousenumbers(String optString) {
		LinkedHashSet<String> result = new LinkedHashSet<>(); 
		for(Replacer replacer : housenumberReplacers) {
			try {
				Collection<String> replace = replacer.replace(optString);
				if(replace != null) {
					result.addAll(replace);
				}
			}
			catch (Exception e) {
				LoggerFactory.getLogger(getClass()).warn("Exception in Replacer", e);
			}
		}
		
		return result;
	}

}
