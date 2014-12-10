package me.osm.gazetteer.web.api;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import me.osm.gazetteer.web.ESNodeHodel;
import me.osm.gazetteer.web.imp.IndexHolder;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortBuilders;
import org.json.JSONArray;
import org.json.JSONObject;
import org.restexpress.Request;
import org.restexpress.Response;

public class FeatureAPI {
	
	public JSONObject read(Request request, Response response) 
			throws IOException {
		
		String id = request.getHeader("id");

		boolean withRelated = request.getHeader("_related") != null;
		
		JSONObject feature = getFeature(id, withRelated);
		
		if(feature != null) {
			return feature;
		}
		
		response.setResponseCode(404);
		return null;
	}

	public static JSONObject getFeature(String idParam, boolean withRelated) {
		Client client = ESNodeHodel.getClient();
		
		if(idParam == null) {
			return null;
		}
		
		QueryBuilder q = QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(), 
				FilterBuilders.orFilter(FilterBuilders.termsFilter("feature_id", idParam), FilterBuilders.termsFilter("id", idParam)) );
		
		SearchResponse searchResponse = client.prepareSearch("gazetteer")
			.setSize(10)
			.setQuery(q).get();
		
		SearchHit[] hits = searchResponse.getHits().getHits();
		
		if(hits.length > 0) {
			
			JSONObject feature = mergeIntoFeature(hits);

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

	private static JSONObject getRelated(JSONObject feature) {

		String id = feature.getString("feature_id");
		Client client = ESNodeHodel.getClient();
		
		JSONObject result = new JSONObject();
		JSONArray sameBuilding = new JSONArray();
		JSONArray samePoiType = new JSONArray();
		
		if(id.startsWith("adrpnt")) {
			sameBuilding(id, client, sameBuilding);
		}
		else if(id.startsWith("poipnt")) {
			
			JSONObject addr = feature.getJSONArray("addresses").getJSONObject(0);
			JSONArray jsonArray = addr.getJSONObject("refs").getJSONArray("poi_addresses");
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
		
		result.put("_same_building", sameBuilding);
		result.put("_same_poi_type", samePoiType);
		
		return result;
	}

	private static void sameType(String curentFeatureId, Collection<String> types, JSONObject point, Client client,
			JSONArray result) {
		
		QueryBuilder q = QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(), 
				FilterBuilders.andFilter(
						//same class
						FilterBuilders.termsFilter("poi_class", types),
						//no more than 5km far away 
						FilterBuilders.geoDistanceFilter("center_point")
							.point(point.getDouble("lat"), point.getDouble("lon")).distance("5km"),
						//and not the original poi
						FilterBuilders.notFilter(FilterBuilders.termFilter("feature_id", curentFeatureId))));
	
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
				FilterBuilders.andFilter(
						FilterBuilders.termFilter("refs.poi_addresses", id),
						FilterBuilders.notFilter(FilterBuilders.termFilter("poi_addr_match", "nearest")))
				
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
        "center_point"
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
	
	private static JSONObject mergeIntoFeature(SearchHit[] hits) {
		
		JSONObject result = null;
		JSONArray addresses = new JSONArray();
		
		for(SearchHit hitsrc : hits) {
			
			JSONObject hit = new JSONObject(hitsrc.getSourceAsString());
			
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

}
