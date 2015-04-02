package me.osm.gazetteer.web.api;

import static me.osm.gazetteer.web.api.utils.RequestUtils.getDoubleHeader;
import static me.osm.gazetteer.web.api.utils.RequestUtils.getList;
import static me.osm.gazetteer.web.api.utils.RequestUtils.getSet;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import me.osm.gazetteer.web.ESNodeHodel;
import me.osm.gazetteer.web.Main;
import me.osm.gazetteer.web.api.imp.QToken;
import me.osm.gazetteer.web.api.imp.Query;
import me.osm.gazetteer.web.api.imp.QueryAnalyzer;
import me.osm.gazetteer.web.api.utils.APIUtils;
import me.osm.gazetteer.web.api.utils.BuildSearchQContext;
import me.osm.gazetteer.web.api.utils.Paginator;
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
import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.DisMaxQueryBuilder;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.FuzzyQueryBuilder;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
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

	/**
	 * Create strict query.
	 * Default value is false.
	 * */
	public static final String STRICT_SEARCH_HEADER = "strict";

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
	 * Array members will be added using OR
	 * */
	public static final String REFERENCES_HEADER = "filter";
	
	/**
	 * Use it, if you have separate addresses parts texts, to search over.
	 * */
	public static final String PARTS_HEADER = "parts";
	
	/*
	 * Search and fuzzy housenumbers
	 * */
	protected List<Replacer> housenumberReplacers = new ArrayList<>();
	{
		ReplacersCompiler.compile(housenumberReplacers, new File("config/replacers/hnSearchReplacers"));
	}

	private static final Logger log = LoggerFactory.getLogger(SearchAPI.class);
	
	protected QueryAnalyzer queryAnalyzer = new QueryAnalyzer();
	
	/**
	 * REST Express read routine method
	 * */
	public JSONObject read(Request request, Response response) throws IOException  {
		return read(request, response, false);
	}
	
	/**
	 * Parse request, create and execute query, encode and return results. 
	 * 
	 * @param request RESTExpress request
	 * @param response RestExpress response
	 * @param resendedAfterFail shows that it is a second request, sent after strict request failed
	 * 
	 * @return Search results encoded with {@link JSONObject}}
	 * */
	public JSONObject read(Request request, Response response, boolean resendedAfterFail) 
			throws IOException {

		boolean explain = "true".equals(request.getHeader(EXPLAIN_HEADER));
		String querryString = StringUtils.stripToNull(request.getHeader(Q_HEADER));
		
		Set<String> types = getSet(request, TYPE_HEADER);
		String hname = request.getHeader("hierarchy");
		
		Set<String> poiClass = getSet(request, POI_CLASS_HEADER);
		addPOIGroups(request, poiClass, hname);
		
		Double lat = getDoubleHeader(LAT_HEADER, request);
		Double lon = getDoubleHeader(LON_HEADER, request);
		
		Set<String> refs = getSet(request, REFERENCES_HEADER);
		
		boolean strictRequested = request.getHeader(STRICT_SEARCH_HEADER) != null && 
				Boolean.parseBoolean(request.getHeader(STRICT_SEARCH_HEADER));
		
		boolean fullGeometry = request.getHeader(FULL_GEOMETRY_HEADER) != null 
				&& "true".equals(request.getParameter(FULL_GEOMETRY_HEADER));

		try {
			
			if(querryString == null && poiClass.isEmpty() && types.isEmpty() && refs.isEmpty()) {
				return null;
			}
			
			Query query = queryAnalyzer.getQuery(querryString);
			
			List<JSONObject> poiType = null;
			if(query != null) {
				poiType = findPoiClass(query);
			}
			
			// Strict if strict is requested or this query wasn't yet been resended after fail
			boolean strict = strictRequested ? true : !resendedAfterFail;
			
			SearchRequestBuilder searchRequest = buildSearchRequest(request, strict,
					explain, types, poiClass,
					lat, lon, refs, query);
			
			Paginator.applyPaging(request, searchRequest);
			
			SearchResponse searchResponse = searchRequest.execute().actionGet();
			
			if(searchResponse.getHits().getHits().length == 0) {
				if(Main.config().isReRestrict() && !strictRequested && !resendedAfterFail) {
					return read(request, response, true);
				}
			}
			
			JSONObject answer = APIUtils.encodeSearchResult(
					searchResponse,	fullGeometry, explain);
			
			answer.put("request", request.getHeader(Q_HEADER));
			if(poiType != null && !poiType.isEmpty()) {
				answer.put("matched_type", new JSONArray(poiType));
			}
			
			answer.put("strict", strict);
			
			Paginator.resultPaging(request, answer);
			
			return answer;
		}
		catch (Exception e) {
			e.printStackTrace();
			response.setException(e);
			response.setResponseCode(500);
			
			return null;
		}
		
	}

	/**
	 * Create search request
	 * 
	 * @param request RESTExpress request
	 * @param strict create a strict request
	 * @param explain add query results explanations
	 * @param types restrict query with types (poipnt, adrpnt and so on)
	 * @param poiClass restrict query with poi classes
	 * @param lat latitude of user's viewport center 
	 * @param lon longitude of user's viewport center 
	 * @param refs restrict request with refs 
	 * @param query analyzed query 
	 * 
	 * @return ElasticSearch SearchRequest
	 * */
	public SearchRequestBuilder buildSearchRequest(Request request, boolean strict,
			boolean explain, Set<String> types, Set<String> poiClass, 
			Double lat, Double lon, Set<String> refs, Query query) {
		
		BoolQueryBuilder q = null;
		BuildSearchQContext buildSearchQContext = new BuildSearchQContext();
		
		if(query != null) {
			q = getSearchQuerry(query, strict, buildSearchQContext);
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
		
		QueryBuilder qb = poiClass.isEmpty() ? QueryBuilders.filteredQuery(q, createPoiFilter(query)) : q;

		boolean sortByHNVariants = false;
		if(buildSearchQContext.getHousenumberVariants() != null) {
			sortByHNVariants = buildSearchQContext.getHousenumberVariants().size() == 1;
		}
		qb = rescore(qb, lat, lon, poiClass, sortByHNVariants);
		
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
		return searchRequest;
	}

	/**
	 * Search for poi classes
	 * 
	 * @param analyzed query
	 * 
	 * @return List of matched poi classes
	 * */
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

	/**
	 * Restrict query with provided reference.
	 * 
	 * Allows you to search only within certain boundaries.
	 * References will be conjuncted using OR. 
	 * Adds terms filter over 'refs' field.
	 * 
	 * @param qb parent query builder
	 * @param refs set of references
	 * 
	 * @return modified query
	 * */
	private QueryBuilder addRefsRestriction(QueryBuilder qb, Set<String> refs) {
		qb = QueryBuilders.filteredQuery(qb, FilterBuilders.termsFilter("refs", refs));
		return qb;
	}

	/**
	 * Filter to search over pois' names.
	 * 
	 * Looks over name.text, poi_class and poi_class_trans.
	 * 
	 * @param querry analyzed query
	 * 
	 * @return filter to search over pois
	 * */
	private FilterBuilder createPoiFilter(Query querry) {
		
		// Мне нужны только те пои, для которых совпал name и/или тип.
		BoolQueryBuilder filterQ = QueryBuilders.boolQuery()
				.must(QueryBuilders.termQuery("type", "poipnt"))
				.must(QueryBuilders.multiMatchQuery(querry.toString(), "name.text", "poi_class", "poi_class_trans"));
		
		OrFilterBuilder orFilter = FilterBuilders.orFilter(
				FilterBuilders.queryFilter(filterQ), 
				FilterBuilders.notFilter(FilterBuilders.termsFilter("type", "poipnt")));
		
		return orFilter;
	}

	/**
	 * Add bounding box restriction to main query.
	 * Will fails, if strings can't be parsed as double.
	 * 
	 * @param qb main query builde
	 * @param bbox list of bbox coordinates
	 * 
	 * @return restricted query
	 * */
	private QueryBuilder addBBOXRestriction(QueryBuilder qb, List<String> bbox) {
		qb = QueryBuilders.filteredQuery(qb, 
				FilterBuilders.geoBoundingBoxFilter("center_point")
				.bottomLeft(Double.parseDouble(bbox.get(1)), Double.parseDouble(bbox.get(0)))
				.topRight(Double.parseDouble(bbox.get(3)), Double.parseDouble(bbox.get(2))));
		return qb;
	}

	/**
	 * Setup scoring.
	 * 
	 * Replace default scoring, with combination of 
	 * original score, geo-distance and weight (object type)
	 * 
	 * @param q original query
	 * @param lat latitude of center for geo-distance scoring
	 * @param lon longitude of center for geo-distance scoring
	 * @param poiClass poi classes
	 * 
	 * @return Builder with rescored query
	 * */
	private static QueryBuilder rescore(QueryBuilder q, Double lat, 
			Double lon, Set<String> poiClass, boolean shortHNFirst) {
		
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

		if(shortHNFirst) {
			String script = "def s = doc['housenumber'].values.size(); \n return (s == 0 ? 1 : 100/s)";
			
			qb.add(ScoreFunctionBuilders.scriptFunction(script, "groovy").setWeight(0.1f));
		}
		
		return qb;
	}

	/**
	 * Add all poi_classes from poi group
	 * 
	 * @param request REST Express request
	 * @param poiClass set of strings where parsed poi classes will be added
	 * @param hname name of osm-doc hierarchy which contains group and poi classes
	 * */
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

	/**
	 * Add commonSearchQ result into main query
	 * used for override from subclasses 
	 * 
	 * @param query analyzed user query
	 * @param strict create strict request
	 * @param buildSearchQContext context (will be filled with some additional info)
	 * 
	 * @return query builder
	 * */
	public BoolQueryBuilder getSearchQuerry(Query query, boolean strict, BuildSearchQContext buildSearchQContext) {
		
		BoolQueryBuilder resultQuery = QueryBuilders.boolQuery();
		
		mainSearchQ(query, resultQuery, strict, buildSearchQContext);
		
		resultQuery.disableCoord(true);
		resultQuery.mustNot(QueryBuilders.termQuery("weight", 0));
		
		return resultQuery;
		
	}

	/**
	 * Creates main search query.
	 * 
	 * @param query analyzed user query
	 * @param resultQuery parent query
	 * @param strict create strict version of query
	 * */
	public void mainSearchQ(Query query, BoolQueryBuilder resultQuery, boolean strict, BuildSearchQContext context) {
		
		int numbers = query.countNumeric();
		
		List<String> nameExact = new ArrayList<String>();
		List<QToken> required = new ArrayList<QToken>();
		LinkedHashSet<String> nums = new LinkedHashSet<String>();
		
		// Try to find housenumbers using hnSearchReplacers
		Collection<String> housenumbers = fuzzyNumbers(query.woFuzzy().toString());
		context.setHousenumberVariants(housenumbers);

		// If those numbers were found
		// Add those numbers into subquery
		// Longer variants will have score boost
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
			}
			numberQ.should(QueryBuilders.matchQuery("search", housenumbers).boost(10));
			
			// if there is more then one number term, boost query over street names.
			// for example in query "City, 8 march, 24" text part should be boosted. 
			int streetNumberMultiplyer = numbers == 1 ? 10 : i++ * 30;
			if(numbers > 1) {
				numberQ.should(QueryBuilders.matchQuery("street_name", housenumbers).boost(streetNumberMultiplyer));
			}
			
			resultQuery.must(numberQ.boost(10));
		}
		
		for(QToken token : query.listToken()) {
	
			if(token.isOptional()) {
				
				MultiMatchQueryBuilder option = QueryBuilders.multiMatchQuery(token.toString(), "search", "nearest_neighbour.name");
				
				//Optional but may be important
				if(token.toString().length() > 3) {
					option.boost(5);
				}
				
				resultQuery.should(option);
			}
			else if(token.isNumbersOnly()) {
				
				// Если реплейсеры не распознали номер дома
				// If hnSearch replacers fails to find any housenumbers
				if(housenumbers.isEmpty()) {
					
					// If there is only one number in query, it must be in matched data
					if (numbers == 1) {
						resultQuery.must(QueryBuilders.matchQuery("search", token.toString())).boost(10);
					}
					else {
						resultQuery.should(QueryBuilders.matchQuery("search", token.toString())).boost(10);
					}
				}
			}
			else if(token.isHasNumbers()) {
				// Если реплейсеры не распознали номер дома, то пробуем действовать по старинке.
				// If hnSearch replacers fails to find any housenumbers
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
			else if(!token.isFuzzied()) {
				// Add regular token to the list of required tokens
				required.add(token);
				nameExact.add(token.toString());
			}
			
			if (token.isHasNumbers()) {
				nums.add(token.toString());
			}
			
			if (token.isFuzzied()) {
				// Fuzzied tokens a are required by default 
				required.add(token);
			}
		}
		
		BoolQueryBuilder requiredQ = QueryBuilders.boolQuery();
		requiredQ.disableCoord(true);
		
		for(QToken t : required) {
			if(strict) {
				
				if(t.isFuzzied()) {
					// In strict version one of the term variants must appears in search field
					DisMaxQueryBuilder variantsQ = QueryBuilders.disMaxQuery().boost(20);
					requiredQ.should(variantsQ);
					
					for(String s : t.getVariants()) {
						variantsQ.add(QueryBuilders.matchQuery("search", s).boost(1));
						variantsQ.add(QueryBuilders.matchQuery("street_name", s).boost(10));
						variantsQ.add(QueryBuilders.matchQuery("nearest_neighbour.name", s).boost(5));
					}
				}
				else {
					// In strict version term must appear in document's search field
					requiredQ.should(QueryBuilders.matchQuery("search", t.toString()).boost(20));
				}
			}
			else {
				String term = t.toString();
				
				if(t.isFuzzied()) {
					term = StringUtils.join(t.getVariants(), ' ');
				}
				
				// In not strict variant term must appears in search field or in name of nearby street
				// Also add fuzzines
				QueryBuilder search = QueryBuilders.fuzzyQuery("search", term).boost(20);
				QueryBuilder nearestN = QueryBuilders.matchQuery("nearest_neighbour.name", term).boost(10);
				QueryBuilder nearestS = QueryBuilders.matchQuery("nearby_streets.name", term).boost(0.2f);
				
				if(!t.isFuzzied()) {
					// If term wasn't fuzzied duiring analyze, add fuzzyness
					((FuzzyQueryBuilder) search).fuzziness(Fuzziness.ONE);
				}
				
				requiredQ.should(QueryBuilders.disMaxQuery().tieBreaker(0.4f)
						.add(search)
						.add(nearestS)
						.add(nearestN));
				
			}
		}

		int requiredCount = required.size();
		
		if(strict) {
			// In strict variant all terms must be in search field
			requiredQ.minimumNumberShouldMatch(requiredCount);
		}
		else {
			if(requiredCount > 3) {
				requiredQ.minimumNumberShouldMatch(requiredCount - 2);
			}
			else if(requiredCount >= 2) {
				requiredQ.minimumNumberShouldMatch(requiredCount - 1);
			}
		}
		
		resultQuery.must(requiredQ);
		
		log.trace("Request: {} Required tokens: {} Housenumbers variants: {}", 
				new Object[]{query.print(), required, housenumbers});
		
		List<String> cammel = new ArrayList<String>();
		for(String s : nameExact) {
			cammel.add(s);
			cammel.add(StringUtils.capitalize(s));
		}
		
		// Boost for exact object name match
		resultQuery.should(QueryBuilders.termsQuery("name.exact", cammel).boost(10));
		
		// Boost for housenumber match
		resultQuery.should(QueryBuilders.termsQuery("housenumber", nums).boost(250));
		
	}

	/**
	 * Generates housenumbers variants
	 * 
	 * @param hn housenumber part of query or full query
	 * */
	private Collection<String> fuzzyNumbers(String hn) {

		List<String> result = new ArrayList<>();
		
		if(StringUtils.isNotBlank(hn)) {
			LinkedHashSet<String> tr = transformHousenumbers(hn);
			result.addAll(tr);
		}
		
		return result;
	}

	/**
	 * Generates housenumbers variants using housenumberReplacers
	 * see hnSearchReplacers
	 * 
	 * @param optString housenumber part of query or full query
	 * */
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
