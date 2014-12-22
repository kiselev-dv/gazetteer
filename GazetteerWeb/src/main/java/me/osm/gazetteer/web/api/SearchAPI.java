package me.osm.gazetteer.web.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import me.osm.gazetteer.web.ESNodeHodel;
import me.osm.gazetteer.web.utils.OSMDocSinglton;
import me.osm.osmdoc.model.Feature;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.geo.GeoPoint;
import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.OrFilterBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
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
	
	private static volatile boolean distanceScore = false;
	
	private final QueryAnalyzer queryAnalyzer = new QueryAnalyzer();
	
	public JSONObject read(Request request, Response response) 
			throws IOException {

		boolean explain = "true".equals(request.getHeader(EXPLAIN_HEADER));
		String querryString = StringUtils.stripToNull(request.getHeader(Q_HEADER));
		
		BoolQueryBuilder q = null;
			
		Set<String> types = getSet(request, TYPE_HEADER);
		String hname = request.getHeader("hierarchy");
		Set<String> poiClass = getSet(request, POI_CLASS_HEADER);
		addPOIGroups(request, poiClass, hname);
			
		if(querryString == null && poiClass.isEmpty() && types.isEmpty()) {
			return null;
		}
		
		Query query = queryAnalyzer.getQuery(querryString);
		
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
		
		if(request.getHeader(LAT_HEADER) != null && request.getHeader(LON_HEADER) != null && distanceScore) {
			qb = addDistanceScore(request, qb);
		}
		
		List<String> bbox = getList(request, BBOX_HEADER);
		if(!bbox.isEmpty() && bbox.size() == 4) {
			qb = addBBOXRestriction(qb, bbox);
		}
		
		Client client = ESNodeHodel.getClient();
		SearchRequestBuilder searchRequest = client.prepareSearch("gazetteer")
				.setQuery(qb)
				.addSort(SortBuilders.fieldSort("weight").order(SortOrder.DESC))
				.addSort(SortBuilders.scoreSort())
				
				.setExplain(explain);
		
		Double lat = getDoubleHeader(LAT_HEADER, request);
		Double lon = getDoubleHeader(LON_HEADER, request);
		
		if(lat != null && lon != null) {
			searchRequest.addSort(SortBuilders.geoDistanceSort("center_point").point(lat, lon));
		}
		
		searchRequest.setFetchSource(true);
		
		APIUtils.applyPaging(request, searchRequest);
		
		SearchResponse searchResponse = searchRequest.execute().actionGet();
		
		boolean fullGeometry = request.getHeader(FULL_GEOMETRY_HEADER) != null 
		&& "true".equals(request.getParameter(FULL_GEOMETRY_HEADER));
		
		JSONObject answer = APIUtils.encodeSearchResult(
				searchResponse,	fullGeometry, explain);
		
		answer.put("request", request.getHeader(Q_HEADER));
		
		APIUtils.resultPaging(request, answer);
		
		return answer;
		
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

	private QueryBuilder addDistanceScore(Request request, QueryBuilder q) {
		Double lat = Double.parseDouble(request.getHeader(LAT_HEADER));
		Double lon = Double.parseDouble(request.getHeader(LON_HEADER));

		return addDistanceScore(q, lat, lon);
	}

	private static QueryBuilder addDistanceScore(QueryBuilder q, Double lat, Double lon) {
		QueryBuilder qb = QueryBuilders.functionScoreQuery(q, 
				ScoreFunctionBuilders.linearDecayFunction("center_point", 
						new GeoPoint(lat, lon), "200km")).boostMode(CombineFunction.AVG)
							.scoreMode("max");
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
		for(QToken token : query.listToken()) {
			
			if(token.isOptional()) {
				resultQuery.should(QueryBuilders.matchQuery("search", token.toString()));
			}
			
			else if(token.isNumbersOnly()) {
				if (numbers == 1) {
					resultQuery.must(QueryBuilders.matchQuery("search", token.toString())).boost(10);
				}
				else {
					resultQuery.should(QueryBuilders.matchQuery("search", token.toString())).boost(10);
				}
			}
			
			else {
				resultQuery.must(QueryBuilders.matchQuery("search", token.toString()));
			}
		}
		
		if (numbers > 1) {
			resultQuery.minimumNumberShouldMatch(numbers - 1);
		}
	}

	public static void setDistanceScoring(boolean value) {
		distanceScore = value;
	}

}
