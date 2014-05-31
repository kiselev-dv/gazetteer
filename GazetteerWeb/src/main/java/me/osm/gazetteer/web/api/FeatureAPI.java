package me.osm.gazetteer.web.api;

import java.io.IOException;

import me.osm.gazetteer.web.ESNodeHodel;
import me.osm.gazetteer.web.imp.Importer;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.json.JSONArray;
import org.json.JSONObject;
import org.restexpress.Request;

public class FeatureAPI {

	private static final String[] COMMON_FIELDS = new String[]{
		"feature_id",
		"type",
		"timestamp",
		"name",
		"alt_names",
		"nearby_streets",
		"nearby_places",
		"tags",
        "poi_class",
        "poi_class_names",
        "operator",
        "brand",
        "opening_hours",
        "phone",
        "email",
        "website",
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

	public JSONObject request(Request request) 
			throws IOException {
		
		Client client = ESNodeHodel.getClient();
		
		String idParam = request.getHeader("id");
		
		QueryBuilder q = QueryBuilders.constantScoreQuery(
				FilterBuilders.termsFilter("feature_id", idParam));
		
		SearchResponse searchResponse = client.prepareSearch("gazetteer").setTypes(Importer.TYPE_NAME)
			.setSearchType(SearchType.QUERY_AND_FETCH).setSize(1000)
			.setQuery(q)
			.execute().actionGet();
		
		SearchHit[] hits = searchResponse.getHits().getHits();
		
		if(hits.length > 0) {
			JSONObject feature = mergeIntoFeature(hits);
			
			return feature;
		}
		
		return null;
	}

	private JSONObject mergeIntoFeature(SearchHit[] hits) {
		
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
