package me.osm.gazetteer.web.api;

import static me.osm.gazetteer.web.api.utils.RequestUtils.getDoubleHeader;
import static me.osm.gazetteer.web.api.utils.RequestUtils.getList;
import static me.osm.gazetteer.web.api.utils.RequestUtils.getSet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import me.osm.gazetteer.web.ESNodeHolder;
import me.osm.gazetteer.web.GazetteerWeb;
import me.osm.gazetteer.web.api.meta.Endpoint;
import me.osm.gazetteer.web.api.meta.Parameter;
import me.osm.gazetteer.web.api.query.Query;
import me.osm.gazetteer.web.api.query.QueryAnalyzer;
import me.osm.gazetteer.web.api.search.SearchBuilder;
import me.osm.gazetteer.web.api.utils.APIUtils;
import me.osm.gazetteer.web.api.utils.BuildSearchQContext;
import me.osm.gazetteer.web.api.utils.Paginator;
import me.osm.gazetteer.web.api.utils.RequestUtils;
import me.osm.gazetteer.web.imp.IndexHolder;
import me.osm.gazetteer.web.utils.OSMDocSinglton;
import me.osm.osmdoc.model.Feature;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchPhaseExecutionException;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.geo.GeoPoint;
import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.OrFilterBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortBuilders;
import org.json.JSONArray;
import org.json.JSONObject;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.domain.metadata.UriMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

public class SearchAPI implements DocumentedApi {

	/**
	 * OSM Doc hierarchy name.
	 * */
	public static final String HIERARCHY_CODE_HEADER = "hierarchy";

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
	 * Poi tag filters
	 * */
	public static final String POI_TAGS_FILTER_HEADER = "tag_filters";
	
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
	 * Not Supported yet.
	 * */
	public static final String PARTS_HEADER = "parts";
	
	/**
	 * Don't search for POIs
	 * */
	public static final String ADDRESSES_ONLY_HEADER = "only_address";

	/**
	 * How many details should contains answer.
	 * */
	public static final String ANSWER_DETALIZATION_HEADER = "detalization";
	

	private static final Logger log = LoggerFactory.getLogger(SearchAPI.class);

	public SearchAPI() {
		GazetteerWeb.injector().injectMembers(this);
	}
	
	@Inject
	protected QueryAnalyzer queryAnalyzer;
	
	@Inject
	private Paginator paginator;
	
	@Inject
	private SearchBuilder searchBuilder;

	private String apiDefaultHierarchy;
	
	/**
	 * REST Express read routine method
	 * 
	 * @param request REST Express request
	 * @param response REST Express response
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
		apiDefaultHierarchy = GazetteerWeb.osmdocProperties().getApiDefaultHierarchy();
		String hname = request.getHeader(HIERARCHY_CODE_HEADER);
		if (hname == null) {
			hname = apiDefaultHierarchy;
		}
		
		Set<String> poiClass = getSet(request, POI_CLASS_HEADER);
		addPOIGroups(request, poiClass, hname);
		
		Double lat = getDoubleHeader(LAT_HEADER, request);
		Double lon = getDoubleHeader(LON_HEADER, request);
		
		Set<String> refs = getSet(request, REFERENCES_HEADER);
		
		boolean strictRequested = RequestUtils.getBooleanHeader(request, STRICT_SEARCH_HEADER, false);
		
		boolean fullGeometry = RequestUtils.getBooleanHeader(request, FULL_GEOMETRY_HEADER, false);

		boolean addressesOnly = RequestUtils.getBooleanHeader(request, ADDRESSES_ONLY_HEADER, false);
		
		AnswerDetalization detalization = RequestUtils.getEnumHeader(request, 
				ANSWER_DETALIZATION_HEADER, AnswerDetalization.class, AnswerDetalization.FULL);
		
		JSONObject poiTagFilters = null;
		String poiTagFiltersString = request.getHeader(POI_TAGS_FILTER_HEADER);
		if(StringUtils.isNotEmpty(poiTagFiltersString)) {
			poiTagFilters = new JSONObject(poiTagFiltersString);
		}
		
		List<String> bbox = getList(request, BBOX_HEADER);
		
		try {
			
			JSONObject answer = internalSearch(request, response,
					resendedAfterFail, explain, querryString, types, poiClass,
					lat, lon, refs, strictRequested, fullGeometry,
					addressesOnly, detalization, bbox, poiTagFilters);
			
			return answer;
		}
		catch (Exception e) {
			e.printStackTrace();
			response.setException(e);
			response.setResponseCode(500);
			
			return null;
		}
		
	}

	public JSONObject internalSearch(
			boolean explain, String querryString,
			Set<String> types, Set<String> poiClass, Double lat, Double lon,
			Set<String> refs, boolean strict, boolean fullGeometry,
			boolean addressesOnly, AnswerDetalization detalization,
			List<String> bbox, JSONObject poiTagFilters) throws IOException {
		
		if(types == null) {
			types = new HashSet<>();
		}
		if(poiClass == null) {
			poiClass = new HashSet<>();
		}
		if(refs == null) {
			refs = new HashSet<>();
		}
		if(bbox == null) {
			bbox = new ArrayList<>();
		}
		if(detalization == null) {
			detalization = AnswerDetalization.FULL;
		}
		boolean resended = false;
		if(!strict) {
			resended = true;
		}
		
		return internalSearch(
				null, null, resended, explain, querryString, types, 
				poiClass, lat, lon, refs, strict, fullGeometry, 
				addressesOnly, detalization, bbox, poiTagFilters);
	}
	
	private JSONObject internalSearch(Request request, Response response,
			boolean resendedAfterFail, boolean explain, String querryString,
			Set<String> types, Set<String> poiClass, Double lat, Double lon,
			Set<String> refs, boolean strictRequested, boolean fullGeometry,
			boolean addressesOnly, AnswerDetalization detalization,
			List<String> bbox, JSONObject poiTagFilters) throws IOException {
		
		if(querryString == null && poiClass.isEmpty() && types.isEmpty() && refs.isEmpty()) {
			return null;
		}
		
		Query query = queryAnalyzer.getQuery(querryString);
		
		List<JSONObject> poiType = null;
		
		//don't look for poi type if we search only for addresses 
		if(query != null && !addressesOnly) {
			poiType = findPoiClass(query);
		}
		
		// Strict if strict is requested or this query wasn't yet been resended after fail
		boolean strict = strictRequested ? true : !resendedAfterFail;
		
		SearchRequestBuilder searchRequest = buildSearchRequest(bbox, strict,
				explain, types, poiClass, poiTagFilters, addressesOnly,
				lat, lon, refs, query);
		
		log.trace("Search request: {}", searchRequest);
		
		if (request != null) {
			paginator.patchSearchQ(request, searchRequest);
		}
		
		try {
			
			SearchResponse searchResponse = searchRequest.execute().actionGet();
			
			if(request != null && response != null) {
				if(searchResponse.getHits().getHits().length == 0) {
					if(GazetteerWeb.config().isReRestrict() && !strictRequested && !resendedAfterFail) {
						return read(request, response, true);
					}
				}
			}
			
			if(explain) {
				for(SearchHit hit : searchResponse.getHits().getHits()) {
					log.debug("{} strict={} result={} \nexplanations={}",
							query.toString(), strict, hit.getSource().get("search"), hit.explanation().toString());
				}
			}
			
			JSONObject answer = APIUtils.encodeSearchResult(
					searchResponse,	fullGeometry, explain, detalization);
			
			answer.put("request", StringEscapeUtils.escapeHtml4(querryString));
			
			if(poiType != null && !poiType.isEmpty()) {
				answer.put("matched_type", new JSONArray(poiType));
			}
			
			answer.put("strict", strict);
			
			if (request != null) {
				paginator.patchAnswer(request, answer);
			}
			
			return answer;
		}
		catch(SearchPhaseExecutionException spe) {
			log.error("Failed to execute query: {}", searchRequest, spe);
			throw spe;
		}
	}

	/**
	 * Create search request
	 *
	 * @param bbox 
	 * @param strict create a strict request
	 * @param explain add query results explanations
	 * @param types restrict query with types (poipnt, adrpnt and so on)
	 * @param poiClass restrict query with poi classes
	 * @param poiTagFilters 
	 * @param lat latitude of user's viewport center 
	 * @param lon longitude of user's viewport center 
	 * @param refs restrict request with refs 
	 * @param query analyzed query 
	 * @param addressesOnly don't search for POIs
	 * 
	 * @return ElasticSearch SearchRequest
	 * */
	public SearchRequestBuilder buildSearchRequest(List<String> bbox, boolean strict,
			boolean explain, Set<String> types, Set<String> poiClass, 
			JSONObject poiTagFilters, boolean addressesOnly, Double lat, Double lon, 
			Set<String> refs, Query query) {
		
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
		
		if(poiTagFilters != null) {
			q.must(buildPoiTagsFilter(poiTagFilters));
		}
		
		if(addressesOnly) {
			q.mustNot(QueryBuilders.termQuery("type", "poipnt"));
		}
		
		// if poiClass.isEmpty() try to search over objcts names
		// Otherwise q should contains filters over poi types
		QueryBuilder qb = null;
		if(poiClass.isEmpty() && !addressesOnly) {
			qb = QueryBuilders.filteredQuery(q, createPoiFilter(query));
		}
		else {
			qb = q;
		}

		boolean sortByHNVariants = false;
		if(buildSearchQContext.getHousenumberVariants() != null && strict) {
			sortByHNVariants = buildSearchQContext.getHousenumberVariants().size() == 1;
		}
		
		// Do not rescore with distance if we are in debug mode, 
		// because rescore will erase match query scoring 
		if(!explain && (lat != null || lon != null || poiClass != null)) {
			qb = rescore(qb, lat, lon, poiClass, sortByHNVariants);
		}
		
		if(!bbox.isEmpty() && bbox.size() == 4) {
			qb = addBBOXRestriction(qb, bbox);
		}
		
		if(!refs.isEmpty()) {
			qb = addRefsRestriction(qb, refs);
		}
		
		Client client = ESNodeHolder.getClient();
		SearchRequestBuilder searchRequest = client
				.prepareSearch("gazetteer").setTypes(IndexHolder.LOCATION)
				.setQuery(qb)
				.setExplain(explain);
		
		searchRequest.addSort(SortBuilders.scoreSort());

		searchRequest.setFetchSource(true);
		return searchRequest;
	}

	@SuppressWarnings("unchecked")
	private QueryBuilder buildPoiTagsFilter(JSONObject poiTagFilters) {
		
		BoolQueryBuilder res = QueryBuilders.boolQuery();
		
		for(String key : (Set<String>)poiTagFilters.keySet()) {
			if("opening_hours".equals(key)) {
				JSONArray val = poiTagFilters.optJSONArray(key);
				if(val.length() > 0 && "24_7".equals(val.getString(0))) {
					res.must(QueryBuilders.termQuery("more_tags.opening_hours.24_7", true));
				}
			}
			else {
				Object val = poiTagFilters.get(key);
				if(val instanceof JSONArray) {
					JSONArray valArray = ((JSONArray)val);
					List<String> options = new ArrayList<>();
					for(int i = 0; i < valArray.length(); i++ ) {
						options.add(valArray.getString(i));
					}
					res.must(QueryBuilders.termsQuery("more_tags." + key, options));
				}
				else {
					res.must(QueryBuilders.termQuery("more_tags." + key, true));
				}
			}
		}
		
		return res;
	}

	/**
	 * Search for poi classes
	 * 
	 * @param analyzed query
	 * 
	 * @return List of matched poi classes
	 * */
	protected List<JSONObject> findPoiClass(Query query) {
		
		Client client = ESNodeHolder.getClient();
		
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
		
		qb.add(ScoreFunctionBuilders.scriptFunction("score", "expression").setWeight(10));

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
	public static void addPOIGroups(Request request, Set<String> poiClass, String hname) {
		for(String s : getSet(request, POI_GROUP_HEADER)) {
			Collection<? extends Feature> hierarcyBranch = 
					OSMDocSinglton.get().getReader().getHierarcyBranch(hname, s);
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
		
		searchBuilder.mainSearchQ(query, resultQuery, strict, buildSearchQContext);
		
		return resultQuery;
		
	}

	@Override
	public Endpoint getMeta(UriMetadata uriMetadata) {
		
		Endpoint meta = new Endpoint(uriMetadata.getPattern(), "locations search", 
				"Searches for objects.");
		
		meta.getUrlParameters().add(new Parameter(Q_HEADER, "Querry text"));
		meta.getUrlParameters().add(new Parameter(STRICT_SEARCH_HEADER, 
				"Create strict query. Default value is false."));
		meta.getUrlParameters().add(new Parameter(EXPLAIN_HEADER, 
				"Explain search results score. Default value is false."));
		meta.getUrlParameters().add(new Parameter(TYPE_HEADER, 
				"Type of feature. [adrpnt, poipnt, hghnet, plcpnt, admbnd]"
			  + " Multiple values are combined via OR."));
		meta.getUrlParameters().add(new Parameter(FULL_GEOMETRY_HEADER, 
				"Include or not full geometry of object. Default is not include."));
		meta.getUrlParameters().add(new Parameter(BBOX_HEADER, 
				"Search inside given BBOX only. [west, south, east, north]"));
		meta.getUrlParameters().add(new Parameter(POI_CLASS_HEADER, 
				"Look for pois of exact types. May contains multiple values. "
			  + "Codes are searched among poi hierarchy provided via '" + HIERARCHY_CODE_HEADER + "'"));
		meta.getUrlParameters().add(new Parameter(POI_GROUP_HEADER, 
				"Look for pois of exact types "
			  + "(Groups will be expanded and merged with '" + POI_CLASS_HEADER + "' header). "
			  + "May contains multiple values. "
			  + "Codes are searched among poi hierarchy provided via '" + HIERARCHY_CODE_HEADER + "'"));
		meta.getUrlParameters().add(new Parameter(HIERARCHY_CODE_HEADER, 
				"Code of OSM Doc hierarchy. Used for POI types search."));
		meta.getUrlParameters().add(new Parameter(LAT_HEADER, 
				"Latitude of map center, used for distance scoring. "
			  + "Switch off distance score if absent."));
		meta.getUrlParameters().add(new Parameter(LON_HEADER, 
				"Longitude of map center, used for distance scoring. "
			  + "Switch off distance score if absent."));
		meta.getUrlParameters().add(new Parameter(REFERENCES_HEADER, 
				"Features id's of higher objects to filter results. "
			  + "In other words will search over those object, "
			  + "which have provided boundaries or street as part of address. " 
	          + "Array members will be added using OR."
			  + "Switch off distance score if absent."));
		meta.getUrlParameters().add(new Parameter(ADDRESSES_ONLY_HEADER, 
				"Search only for addresses, don't search for POIs."));
		meta.getUrlParameters().add(new Parameter(ANSWER_DETALIZATION_HEADER, 
				"How many details should contains answer. full/short"));
		
		return meta;
	}

}
