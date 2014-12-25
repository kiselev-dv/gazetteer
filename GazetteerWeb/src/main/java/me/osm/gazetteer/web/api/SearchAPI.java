package me.osm.gazetteer.web.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import me.osm.gazetteer.web.ESNodeHodel;
import me.osm.gazetteer.web.imp.Importer;
import me.osm.gazetteer.web.imp.IndexHolder;
import me.osm.gazetteer.web.utils.OSMDocSinglton;
import me.osm.osmdoc.model.Feature;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.geo.GeoDistance;
import org.elasticsearch.common.geo.GeoPoint;
import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.OrFilterBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.json.JSONObject;
import org.restexpress.Request;
import org.restexpress.Response;

public class SearchAPI {

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
	
	protected final QueryAnalyzer queryAnalyzer = new QueryAnalyzer();
	
	public JSONObject read(Request request, Response response) 
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
			
			if(querryString == null && poiClass.isEmpty() && types.isEmpty()) {
				return null;
			}
			
			Query query = queryAnalyzer.getQuery(querryString);
			
			JSONObject poiType = findPoiClass(query);
			if(poiType != null) {
				poiClass.add(poiType.getString("name"));
			}
			
			if(querryString != null) {
				q = getSearchQuerry(query);
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
			
			Client client = ESNodeHodel.getClient();
			SearchRequestBuilder searchRequest = client
					.prepareSearch("gazetteer").setTypes(IndexHolder.LOCATION)
					.setQuery(qb)
					.setExplain(explain);
			
			searchRequest.addSort(SortBuilders.scoreSort());

			searchRequest.setFetchSource(true);
			
			APIUtils.applyPaging(request, searchRequest);
			
			SearchResponse searchResponse = searchRequest.execute().actionGet();
			
			boolean fullGeometry = request.getHeader(FULL_GEOMETRY_HEADER) != null 
					&& "true".equals(request.getParameter(FULL_GEOMETRY_HEADER));
			
			JSONObject answer = APIUtils.encodeSearchResult(
					searchResponse,	fullGeometry, explain);
			
			answer.put("request", request.getHeader(Q_HEADER));
			if(poiType != null) {
				answer.put("matched_type", request.getHeader(Q_HEADER));
			}
			
			APIUtils.resultPaging(request, answer);
			
			return answer;
		}
		catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
		
	}

	protected JSONObject findPoiClass(Query query) {
		
		Client client = ESNodeHodel.getClient();
		
		String qs = query.filter(new HashSet<String>(Arrays.asList("на", "дом"))).toString();
		
		SearchRequestBuilder searchRequest = client.prepareSearch("gazetteer").setTypes(IndexHolder.POI_CLASS)
				.setQuery(QueryBuilders.multiMatchQuery(qs, "translated_title", "keywords"));
		
		SearchHit[] hits = searchRequest.get().getHits().getHits();

		if(hits.length > 0) {
			return new JSONObject(hits[0].sourceAsString());
		}
		
		return null;
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
				.must(QueryBuilders.multiMatchQuery(querry, "name", "poi_class", "poi_class_trans"));
		
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
		
		qb.add(ScoreFunctionBuilders.fieldValueFactorFunction("weight").setWeight(0.02f));
		
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

	public BoolQueryBuilder getSearchQuerry(Query query) {
		
		BoolQueryBuilder resultQuery = QueryBuilders.boolQuery();
		
		commonSearchQ(query, resultQuery);
		
		resultQuery.disableCoord(true);
		resultQuery.mustNot(QueryBuilders.termQuery("weight", 0));
		
		return resultQuery;
		
	}

	public void commonSearchQ(Query query, BoolQueryBuilder resultQuery) {
		int numbers = query.countNumeric();
		
		List<String> required = new ArrayList<String>();
		for(QToken token : query.listToken()) {
			//optional
			if(token.isOptional()) {
				resultQuery.should(QueryBuilders.matchQuery("search", token.toString()));
			}
			//number
			else if(token.isNumbersOnly()) {
				if (numbers == 1) {
					resultQuery.must(QueryBuilders.matchQuery("search", token.toString())).boost(10);
				}
				else {
					resultQuery.should(QueryBuilders.matchQuery("search", token.toString())).boost(10);
				}
			}
			//regular token
			else {
				resultQuery.must(QueryBuilders.matchQuery("search", token.toString()));
				required.add(token.toString());
			}
		}
		
		List<String> cammel = new ArrayList<String>();
		for(String s : required) {
			cammel.add(s);
			cammel.add(StringUtils.capitalize(s));
		}
		
		resultQuery.should(QueryBuilders.termsQuery("name.exact", cammel).boost(20));
		
		if (numbers > 1) {
			resultQuery.minimumNumberShouldMatch(numbers - 1);
		}
	}

}
