package me.osm.gazetteer.web.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.osm.gazetteer.web.ESNodeHodel;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.geo.ShapeRelation;
import org.elasticsearch.common.geo.builders.ShapeBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.FilteredQueryBuilder;
import org.elasticsearch.index.query.GeoShapeFilterBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.json.JSONObject;
import org.restexpress.Request;
import org.restexpress.Response;


public class InverseGeocodeAPI {
	
	public JSONObject read(Request request, Response response){
		
		JSONObject result = new JSONObject();
		
		double lon = Double.parseDouble(request.getHeader("lon"));
		double lat = Double.parseDouble(request.getHeader("lat"));
		
		Client client = ESNodeHodel.getClient();
		
		
		GeoShapeFilterBuilder filter = FilterBuilders.geoShapeFilter("full_geometry", ShapeBuilder.newPoint(lon, lat), ShapeRelation.INTERSECTS);
		
		FilteredQueryBuilder q =
				QueryBuilders.filteredQuery(
						QueryBuilders.matchAllQuery(),
						filter);
		
		SearchRequestBuilder searchRequest = client.prepareSearch("gazetteer").setQuery(q);
		
		SearchResponse searchResponse = searchRequest.get();
				
		SearchHit[] hits = searchResponse.getHits().getHits();
		List<JSONObject> boundaries = new ArrayList<JSONObject>();
		
		
		boolean fullGeometry = request.getHeader(SearchAPI.FULL_GEOMETRY_HEADER) != null 
				&& "true".equals(request.getParameter(SearchAPI.FULL_GEOMETRY_HEADER));
		
		Map<String, JSONObject> levels = new HashMap<String, JSONObject>();
		for(SearchHit hit : hits) {
			JSONObject obj = new JSONObject(hit.getSourceAsString());

			if(!fullGeometry) {
				obj.remove("full_geometry");
			}
			
			boundaries.add(obj);
			levels.put(obj.optString("addr_level"), obj);
		}
		
		List<String> parts = new ArrayList<String>();
		
		if(levels.containsKey("admin0")) {
			parts.add(levels.get("admin0").optString("name"));
		}
		if(levels.containsKey("admin1")) {
			parts.add(levels.get("admin1").optString("name"));
		}
		if(levels.containsKey("admin2")) {
			parts.add(levels.get("admin2").optString("name"));
		}
		if(levels.containsKey("local_admin")) {
			parts.add(levels.get("local_admin").optString("name"));
		}
		if(levels.containsKey("locality")) {
			parts.add(levels.get("locality").optString("name"));
		}
		if(levels.containsKey("neighborhood")) {
			parts.add(levels.get("neighborhood").optString("name"));
		}
		
		result.put("boundaries", new JSONObject(levels));
		result.put("text", StringUtils.join(parts, ", "));
		
 		return result;
	}

}
