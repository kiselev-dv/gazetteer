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
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.DisMaxQueryBuilder;
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
		String querry = StringUtils.stripToNull(request.getHeader(Q_HEADER));
		
		BoolQueryBuilder q = null;
			
		Set<String> types = getSet(request, TYPE_HEADER);
		String hname = request.getHeader("hierarchy");
		Set<String> poiClass = getSet(request, POI_CLASS_HEADER);
		addPOIGroups(request, poiClass, hname);
			
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
			qb = addDistanceScore(request, qb);
		}
		
		List<String> bbox = getList(request, BBOX_HEADER);
		if(!bbox.isEmpty() && bbox.size() == 4) {
			qb = addBBOXRestriction(qb, bbox);
		}

		Client client = ESNodeHodel.getClient();
		SearchRequestBuilder searchRequest = client.prepareSearch("gazetteer")
				.setQuery(qb)
				.setExplain(explain);
		
		searchRequest.addField("admin0_name");
		searchRequest.addField("admin0_alternate_names");
		searchRequest.addField("admin1_alternate_names");
		searchRequest.addField("admin2_alternate_names");
		searchRequest.addField("local_admin_name");
		searchRequest.addField("local_admin_alternate_names");
		searchRequest.addField("locality_name");
		searchRequest.addField("locality_alternate_names");
		searchRequest.addField("nearest_place.name");
		searchRequest.addField("neighborhood_name");
		searchRequest.addField("neighborhood_alternate_names");
		searchRequest.addField("nearest_neighbour.name");
		searchRequest.addField("street_name");
		searchRequest.addField("street_alternate_names");
		searchRequest.addField("nearby_streets.name");
		searchRequest.addField("housenumber");
		searchRequest.addField("name");
		searchRequest.addField("keywords");
		searchRequest.addField("type");
		
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

	private QueryBuilder addBBOXRestriction(QueryBuilder qb, List<String> bbox) {
		qb = QueryBuilders.filteredQuery(qb, 
				FilterBuilders.geoBoundingBoxFilter("center_point")
				.bottomLeft(Double.parseDouble(bbox.get(1)), Double.parseDouble(bbox.get(0)))
				.topRight(Double.parseDouble(bbox.get(3)), Double.parseDouble(bbox.get(2))));
		return qb;
	}

	private QueryBuilder addDistanceScore(Request request, QueryBuilder q) {
		QueryBuilder qb;
		Double lat = Double.parseDouble(request.getHeader(LAT_HEADER));
		Double lon = Double.parseDouble(request.getHeader(LON_HEADER));

		qb = QueryBuilders.functionScoreQuery(q, 
				ScoreFunctionBuilders.gaussDecayFunction("center_point", 
						new GeoPoint(lat, lon), "2000km")).scoreMode("max").boostMode("sum");
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

	public static BoolQueryBuilder getSearchQuerry(String querry) {
		
		return QueryBuilders.boolQuery()
			.should(dismax(
					cscore("admin0_name", querry, 10.1f), 
					cscore("admin0_alternate_names", querry, 9.1f)))
			.should(dismax(
					cscore("admin1_name", querry, 10.2f), 
					cscore("admin1_alternate_names", querry, 9.2f)))
			.should(dismax(
					cscore("admin2_name", querry, 10.3f), 
					cscore("admin2_alternate_names", querry, 9.3f)))
			.should(dismax(
					cscore("local_admin_name", querry, 10.4f), 
					cscore("local_admin_alternate_names", querry, 9.4f)))
			.should(dismax(
					cscore("locality_name", querry, 10.5f), 
					cscore("locality_alternate_names", querry, 9.5f),
					cscore("nearest_place.name", querry, 8.5f)))
			.should(dismax(
					cscore("neighborhood_name", querry, 10.6f), 
					cscore("neighborhood_alternate_names", querry, 9.6f),
					cscore("nearest_neighbour.name", querry, 8.6f)))
			.should(dismax(
					cscore("street_name", querry, 10.7f), 
					cscore("street_alternate_names", querry, 9.7f), 
					cscore("nearby_streets.name", querry, 7.7f)))
			.should(cscore("housenumber", querry, 10.8f))
			.should(QueryBuilders.termQuery("type", "adrpnt"))
			//Boost pois by name and keywords
			.should(
					QueryBuilders.filteredQuery(
							dismax(cscore("name", querry, 10.8f), cscore("keywords", querry, 10.8f)), 
							FilterBuilders.termFilter("type", "poipnt")));
		
	}

	private static QueryBuilder dismax(QueryBuilder... querryes) {
		DisMaxQueryBuilder dm = QueryBuilders.disMaxQuery();
		for(QueryBuilder q : querryes) {
			dm.add(q);
		}
		return dm;
	}

	private static QueryBuilder cscore(String field, String querry, float score) {
		
		return QueryBuilders.constantScoreQuery(QueryBuilders.matchQuery(field, querry)).boost(score);
	}

}
