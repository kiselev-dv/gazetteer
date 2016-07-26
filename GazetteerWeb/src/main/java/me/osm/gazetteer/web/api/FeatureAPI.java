package me.osm.gazetteer.web.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import me.osm.gazetteer.web.ESNodeHolder;
import me.osm.gazetteer.web.api.meta.Endpoint;
import me.osm.gazetteer.web.api.meta.Parameter;
import me.osm.gazetteer.web.imp.IndexHolder;
import me.osm.gazetteer.web.stats.APIRequest.APIRequestBuilder;
import me.osm.gazetteer.web.stats.StatWriterUtils;
import me.osm.gazetteer.web.stats.StatisticsWriter;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.json.JSONArray;
import org.json.JSONObject;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.domain.metadata.UriMetadata;

/**
 * Returns full information about object.
 * */
public class FeatureAPI implements DocumentedApi {
	
	public JSONObject read(Request request, Response response) 
			throws IOException {
		
		APIRequestBuilder stat = StatWriterUtils.fillFromRequest(request);
		stat.api("feature");
		
		String id = request.getHeader("id");
		stat.resultId(id);

		boolean withRelated = request.getHeader("_related") != null;
		
		JSONObject feature = getFeature(id, withRelated);
		
		if(feature != null) {
			stat.status(200);
			stat.fillByFeature(feature);
			StatisticsWriter.write(stat.build());
			
			return feature;
		}
		
		stat.status(404);
		StatisticsWriter.write(stat.build());
		response.setResponseCode(404);
		
		return null;
	}

	public static JSONObject getFeature(String idParam, boolean withRelated) {
		Client client = ESNodeHolder.getClient();
		
		if(idParam == null) {
			return null;
		}
		
		QueryBuilder q = QueryBuilders.boolQuery()
				.must(QueryBuilders.matchAllQuery())
				.filter(QueryBuilders.orQuery(
						QueryBuilders.termsQuery("feature_id", idParam), 
						QueryBuilders.termsQuery("id", idParam))); 
		
		SearchResponse searchResponse = client.prepareSearch("gazetteer")
			.setTypes(IndexHolder.LOCATION)
			.setSize(50)
			.setQuery(q).get();
		
		SearchHit[] hits = searchResponse.getHits().getHits();
		
		if(hits.length > 0) {
			
			List<JSONObject> hitObjects = new ArrayList<>(hits.length);
			for(SearchHit hitsrc : hits) {
				hitObjects.add(new JSONObject(hitsrc.getSourceAsString())) ;
			}
			
			JSONObject feature = mergeIntoFeature(hitObjects);

			if(withRelated) {
				JSONObject related =  getRelated(feature);
				if(related != null) {
					feature.put("_related", related);
				}
			}
			
			return feature;
		}
		
		return null;
	}
	
	/**
	 * Merge different address rows with the same feature_id into
	 * one object with array of different addresses.
	 * <p>
	 * PRESERVES original order
	 * <p>
	 * NB: Index contains 1 row for each address, 
	 * but this API returns object (feature) with all addresses
	 * 
	 * @param rows addresses from ES query
	 * @return list of merged objects
	 * */
	public static List<JSONObject> mergeFeaturesByID(List<JSONObject> rows) {
		
		//Use linked hash map to preserve original sorting
		LinkedHashMap<String, List<JSONObject>> sorter = new LinkedHashMap<>();
		
		for(JSONObject obj : rows) {
			String fid = obj.getString("feature_id");
			if(sorter.get(fid) == null ) {
				sorter.put(fid, new ArrayList<JSONObject>());
			}
			
			sorter.get(fid).add(obj);
		}
		
		List<JSONObject> result = new ArrayList<>(sorter.keySet().size());
		
		for(List<JSONObject> feature : sorter.values()) {
			result.add(FeatureAPI.mergeIntoFeature(feature));
		}
		
		return result;
	}
	
	public static JSONObject mergeIntoFeature(List<JSONObject> feature) {
		JSONObject result = null;
		JSONArray addresses = new JSONArray();
		
		for(JSONObject hit : feature) {
			
			if(result == null) {
				result = new JSONObject(hit, COMMON_FIELDS);
			}
			
			addresses.put(new JSONObject(hit, ADDR_ROW_FIELDS));
		}
		
		if(result != null) {
			result.put("addresses", addresses);
		}
		
		return result;
	}

	public static JSONObject getRelated(JSONObject feature) {

		String id = feature.getString("feature_id");
		Client client = ESNodeHolder.getClient();
		
		JSONObject result = new JSONObject();
		JSONArray sameBuilding = new JSONArray();
		JSONArray samePoiType = new JSONArray();
		
		if(id.startsWith("adrpnt")) {
			sameBuilding(id, client, sameBuilding);
		}
		else if(id.startsWith("poipnt")) {
			
			JSONObject addr = feature.getJSONArray("addresses").getJSONObject(0);
			JSONArray jsonArray = addr.getJSONObject("refs").optJSONArray("poi_addresses");
			if(jsonArray != null && jsonArray.length() > 0) {
				sameBuilding(jsonArray.getString(0), client, sameBuilding);
			}
			
			Set<String> types = new HashSet<String>();
			JSONArray poiClassesJSON = feature.getJSONArray("poi_class");
			for(int i = 0; i < poiClassesJSON.length(); i++) {
				types.add(poiClassesJSON.getString(i));
			}
			
			sameType(id, types, feature.getJSONObject("center_point"), client, samePoiType);
		}
		else if(id.startsWith("hghway")) {
			result.put("_ref_hn", referenced4Street(id, client));
		}
		
		result.put("_same_building", sameBuilding);
		result.put("_same_poi_type", samePoiType);
		
		return result;
	}

	private static JSONArray referenced4Street(String id, Client client) {
		
		JSONArray result = new JSONArray();
		
		QueryBuilder q = QueryBuilders.boolQuery()
				.must(QueryBuilders.matchAllQuery())
				.filter(QueryBuilders.andQuery(
						QueryBuilders.termsQuery("refs.street", id), 
						QueryBuilders.termQuery("type", "adrpnt")));
				
				
		SearchRequestBuilder querry = client.prepareSearch("gazetteer")
				.setTypes(IndexHolder.LOCATION)
				.setSize(200)
				.addSort("housenumber", SortOrder.ASC)
				.setQuery(q);
		
		return putReferenced(client, result, querry);
		
	}

	private static JSONArray putReferenced(Client client, JSONArray result,
			SearchRequestBuilder querry) {
		
		SearchResponse searchResponse = querry
				.execute().actionGet();
		
		for(SearchHit hit : searchResponse.getHits().getHits()) {
			JSONObject h = new JSONObject(hit.getSourceAsString());
			result.put(h);
		}
		
		return result;
	}

	private static void sameType(String curentFeatureId, Collection<String> types, JSONObject point, Client client,
			JSONArray result) {
		
		QueryBuilder q = QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(), 
				QueryBuilders.andQuery(
						//same class
						QueryBuilders.termsQuery("poi_class", types),
						//no more than 5km far away 
						QueryBuilders.geoDistanceQuery("center_point")
							.point(point.getDouble("lat"), point.getDouble("lon")).distance("5km"),
						//and not the original poi
						QueryBuilders.notQuery(QueryBuilders.termQuery("feature_id", curentFeatureId))));
	
		SearchRequestBuilder querry = client.prepareSearch("gazetteer")
				.setTypes(IndexHolder.LOCATION)
				.addSort(SortBuilders.geoDistanceSort("center_point").point(point.getDouble("lat"), point.getDouble("lon")))
				.setSize(20)
				.setQuery(q);
		
		SearchResponse searchResponse = querry
				.execute().actionGet();
		
		for(SearchHit hit : searchResponse.getHits().getHits()) {
			JSONObject h = new JSONObject(hit.getSourceAsString());
			result.put(h);
		}
	}

	private static void sameBuilding(String id, Client client,
			JSONArray result) {
		
		QueryBuilder q = QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(), 
				QueryBuilders.andQuery(
						QueryBuilders.termQuery("refs.poi_addresses", id),
						QueryBuilders.notQuery(QueryBuilders.termQuery("poi_addr_match", "nearest")))
				
		);
		
		fillArrayFromResult(client, result, q);
	}

	private static void fillArrayFromResult(Client client,
			JSONArray sameBuilding, QueryBuilder q) {
		
		SearchRequestBuilder querry = client.prepareSearch("gazetteer")
				.setTypes(IndexHolder.LOCATION)
				.setQuery(q);
		
		SearchResponse searchResponse = querry
				.execute().actionGet();
		
		for(SearchHit hit : searchResponse.getHits().getHits()) {
			JSONObject h = new JSONObject(hit.getSourceAsString());
			sameBuilding.put(h);
		}
	}

	private static final String[] COMMON_FIELDS = new String[]{
		"feature_id",
		"type",
		"timestamp",
		"name",
		"alt_names",
		"nearby_streets",
		"nearby_places",
		"nearest_place",
		"nearest_neighbour",
		"tags",
        "poi_class",
        "poi_keywords",
        "more_tags",
        "center_point",
        "full_geometry"
	};

	private static final String[] ADDR_ROW_FIELDS = new String[] {
		"id",
		"address",
		"alt_addresses",
		"scheme",
		"admin0_name",
		"admin0_alternate_names",
		"admin1_name",
		"admin1_alternate_names",
		"admin2_name",
		"admin2_alternate_names",
		"local_admin_name",
		"local_admin_alternate_names",
		"locality_name",
		"locality_alternate_names",
		"neighborhood_name",
		"neighborhood_alternate_names",
		"street_name",
		"street_alternate_names",
		"housenumber",
		"refs"
	};

	@Override
	public Endpoint getMeta(UriMetadata uriMetadata) {
		
		Endpoint meta = new Endpoint(uriMetadata.getPattern(), "Location read", 
				"Returns full information about object.");
		
		meta.getPathParameters().add(new Parameter("id", "Object id (required)."));
		meta.getPathParameters().add(new Parameter("_related", 
				"Return data for related object."));
		
		return meta;
	}

}
