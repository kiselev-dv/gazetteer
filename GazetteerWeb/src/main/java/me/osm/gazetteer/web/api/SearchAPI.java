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
import me.osm.gazetteer.web.utils.OSMDocSinglton;
import me.osm.osmdoc.model.Feature;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.json.JSONObject;
import org.restexpress.Request;
import org.restexpress.Response;

public class SearchAPI {

	private static final String[] mainFields = new String[]{"name", "address", "poi_class", "poi_class_names", "operator", "brand"};
	private static final String[] secondaryFields = new String[]{"parts", "alt_addresses", "alt_names"};
	private static final String[] tertiaryFields = new String[]{"nearby_streets", "nearest_city", "nearest_neighbour"};
	
	/**
	 * Explain search results or not 
	 * (<code>true<code> for explain)
	 * default is false
	 * */
	public static String EXPLAIN_HEADER = "explain";
	
	/**
	 * Querry string
	 * */
	public static String Q_HEADER = "q";
	
	/**
	 * Type of feature. [adrpnt, poipnt etc]
	 * */
	public static String TYPE_HEADER = "type";

	/**
	 * Include or not object full geometry
	 * (<code>true<code> to include)
	 * default is false
	 * */
	public static String FULL_GEOMETRY_HEADER = "full_geometry";

	/**
	 * Search inside given BBOX
	 * west, south, east, north'
	 * */
	public static String BBOX_HEADER = "bbox";
	
	/**
	 * Look for poi of exact types
	 * */
	public static String POI_CLASS_HEADER = "poiclass";

	/**
	 * Look for poi of exact types (from hierarchy branch)
	 * */
	public static String POI_GROUP_HEADER = "poigroup";
	
	/**
	 * Latitude of map center
	 * */
	public static String LAT_HEADER = "lat";

	/**
	 * Longitude of map center
	 * */
	public static String LON_HEADER = "lon";
	
	
	public JSONObject read(Request request, Response response) 
			throws IOException {

		boolean explain = "true".equals(request.getHeader(EXPLAIN_HEADER));
		String querry = request.getHeader(Q_HEADER);
		
		BoolQueryBuilder q = null;
			
		Set<String> types = getSet(request, TYPE_HEADER);

		Set<String> poiClass = getSet(request, POI_CLASS_HEADER);
		addPOIGroups(request, poiClass);
			
		if(querry == null && poiClass.isEmpty()) {
			return null;
		}
		
		if(querry != null) {
			q = getSearchQuerry(querry);
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

		QueryBuilder qb = q;

		if(request.getHeader(LAT_HEADER) != null && request.getHeader(LON_HEADER) != null) {
			Double lat = Double.parseDouble(request.getHeader(LAT_HEADER));
			Double lon = Double.parseDouble(request.getHeader(LON_HEADER));

			Map<String, Object> params = new HashMap<String, Object>();
			params.put("lat", lat);
			params.put("lon", lon);
			
			params.put("radius", 10_000);
			
			qb = QueryBuilders.functionScoreQuery(qb).scoreMode("max").boostMode("avg")
					.add(ScoreFunctionBuilders.scriptFunction(
							"score_with_distance", "expression", params));
			
		}

		
		List<String> bbox = getList(request, BBOX_HEADER);
		if(!bbox.isEmpty() && bbox.size() == 4) {
			qb = QueryBuilders.filteredQuery(qb, 
					FilterBuilders.geoBoundingBoxFilter("center_point")
					.bottomLeft(Double.parseDouble(bbox.get(1)), Double.parseDouble(bbox.get(0)))
					.topRight(Double.parseDouble(bbox.get(3)), Double.parseDouble(bbox.get(2))));
		}

		
		SortBuilder sort = SortBuilders.scoreSort();
		
		Client client = ESNodeHodel.getClient();
		SearchRequestBuilder searchRequest = client.prepareSearch("gazetteer")
				.setQuery(qb)
				.setExplain(explain)
				.addSort(sort);
		
		APIUtils.applyPaging(request, searchRequest);
		
		searchRequest.setMinScore(0.001f);
		
		SearchResponse searchResponse = searchRequest.execute().actionGet();
		
		boolean fullGeometry = request.getHeader(FULL_GEOMETRY_HEADER) != null 
		&& "true".equals(request.getParameter(FULL_GEOMETRY_HEADER));
		
		JSONObject answer = APIUtils.encodeSearchResult(
				searchResponse,	fullGeometry, explain);
		
		answer.put("request", request.getHeader(Q_HEADER));
		
		APIUtils.resultPaging(request, answer);
		
		return answer;
		
	}

	private void addPOIGroups(Request request, Set<String> poiClass) {
		for(String s : getSet(request, POI_GROUP_HEADER)) {
			Collection<? extends Feature> hierarcyBranch = OSMDocSinglton.get().getReader().getHierarcyBranch(null, s);
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

	public static BoolQueryBuilder getSearchQuerry(String querry) {
		BoolQueryBuilder q = QueryBuilders.boolQuery()
				.should(buildAddRowQ(querry).boost(20))
				.should(QueryBuilders.multiMatchQuery(querry, mainFields).boost(15))
				.should(QueryBuilders.multiMatchQuery(querry, secondaryFields).boost(10))
				.should(QueryBuilders.multiMatchQuery(querry, tertiaryFields).boost(5));
		return q;
	}

	public static BoolQueryBuilder buildAddRowQ(String querry) {
		return QueryBuilders.boolQuery()
			.should(QueryBuilders.matchQuery("admin0_name", querry).boost(1))
			.should(QueryBuilders.matchQuery("admin0_alternate_names", querry).boost(0.1f))
			.should(QueryBuilders.matchQuery("admin1_name", querry).boost(2))
			.should(QueryBuilders.matchQuery("admin1_alternate_names", querry).boost(0.2f))
			.should(QueryBuilders.matchQuery("admin2_name", querry).boost(3))
			.should(QueryBuilders.matchQuery("admin2_alternate_names", querry).boost(0.3f))
			.should(QueryBuilders.matchQuery("local_admin_name", querry).boost(4))
			.should(QueryBuilders.matchQuery("local_admin_alternate_names", querry).boost(0.4f))
			.should(QueryBuilders.matchQuery("locality_name", querry).boost(5))
			.should(QueryBuilders.matchQuery("locality_alternate_names", querry).boost(0.4f))
			.should(QueryBuilders.matchQuery("neighborhood_name", querry).boost(6))
			.should(QueryBuilders.matchQuery("neighborhood_alternate_names", querry).boost(0.6f))
			.should(QueryBuilders.matchQuery("street_name", querry).boost(7))
			.should(QueryBuilders.matchQuery("housenumber", querry).boost(100));
	}
	
}
