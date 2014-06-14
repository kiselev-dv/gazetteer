package me.osm.gazetteer.web.api;

import java.util.ArrayList;
import java.util.List;

import me.osm.gazetteer.web.ESNodeHodel;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
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
		
		
		GeoShapeFilterBuilder filter = FilterBuilders.geoIntersectionFilter("location.full_geometry", 
				ShapeBuilder.newPoint(lon, lat));
		
		FilteredQueryBuilder q =
				QueryBuilders.filteredQuery(
						QueryBuilders.matchAllQuery(),
						filter);
		
		SearchResponse searchResponse = 
				client.prepareSearch("gazetteer").setQuery(q).get();
				
		SearchHit[] hits = searchResponse.getHits().getHits();
		List<JSONObject> boundaries = new ArrayList<JSONObject>();
		for(SearchHit hit : hits) {
			JSONObject obj = new JSONObject(hit.getSourceAsString());
			boundaries.add(obj);
		}
		
		result.put("boundaries", boundaries);
		
		return result;
	}

}
