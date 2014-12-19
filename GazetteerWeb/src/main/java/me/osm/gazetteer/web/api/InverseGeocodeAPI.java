package me.osm.gazetteer.web.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import me.osm.gazetteer.web.ESNodeHodel;
import me.osm.gazetteer.web.utils.GeometryUtils;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.geo.ShapeRelation;
import org.elasticsearch.common.geo.builders.ShapeBuilder;
import org.elasticsearch.common.unit.DistanceUnit;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.FilteredQueryBuilder;
import org.elasticsearch.index.query.GeoShapeFilterBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortBuilders;
import org.json.JSONObject;
import org.restexpress.Request;
import org.restexpress.Response;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;

public class InverseGeocodeAPI {
	
	public JSONObject read(Request request, Response response){
		
		JSONObject result = new JSONObject();
		
		double lon = Double.parseDouble(request.getHeader("lon"));
		double lat = Double.parseDouble(request.getHeader("lat"));
		
		boolean wRelated = request.getHeader("related") != null;
		
		boolean fullGeometry = request.getHeader(SearchAPI.FULL_GEOMETRY_HEADER) != null 
				&& "true".equals(request.getParameter(SearchAPI.FULL_GEOMETRY_HEADER));
		
		LinkedHashMap<String, String> parts = new LinkedHashMap<String, String>();

		JSONObject point = getPoint(lon, lat);
		
		if(point != null) {
			if(wRelated) {
				JSONObject related = FeatureAPI.getRelated(point);
				if(related != null) {
					point.put("_related", related);
				}
			}
			
			return point;
		}
		else {
			JSONObject highway = getHighway(lon, lat);
			
			if(highway != null) {
				fillByPoint(parts, highway);
				if (!fullGeometry) {
					highway.remove("full_geometry");
				}
				result.put("highway", highway);
			}
			
			Map<String, JSONObject> levels = getBoundaries(lon, lat);
			
			fillByBoundaries(fullGeometry, parts, levels);
			result.put("boundaries", new JSONObject(levels));
		}
		
		result.put("text", StringUtils.join(parts.values(), ", "));
		result.put("parts", new JSONObject(parts));
		
 		return result;
	}

	private void fillByPoint(LinkedHashMap<String, String> parts, JSONObject point) {
		if(point.has("admin0_name")) {
			parts.put("admin0", point.optString("admin0_name"));
		}
		if(point.has("admin1_name")) {
			parts.put("admin1", point.optString("admin1_name"));
		}
		if(point.has("admin2_name")) {
			parts.put("admin2", point.optString("admin2_name"));
		}
		if(point.has("local_admin_name")) {
			parts.put("local_admin", point.optString("local_admin_name"));
		}
		if(point.has("locality_name")) {
			parts.put("locality", point.optString("locality_name"));
		}
		else if(point.has("nearest_place")) {
			parts.put("locality", point.getJSONObject("nearest_place").optString("name"));
		}
		if(point.has("neighborhood_name")) {
			parts.put("neighborhood", point.optString("neighborhood_name"));
		}
		else if(point.has("nearest_neighborhood")) {
			parts.put("neighborhood", point.getJSONObject("nearest_neighborhood").optString("name"));
		}
		if(point.has("street_name")) {
			parts.put("street", point.optString("street_name"));
		}
		if(point.has("housenumber")) {
			parts.put("housenumber", point.optString("housenumber"));
		}
	}

	private void fillByBoundaries(boolean fullGeometry, LinkedHashMap<String, String> parts,
			Map<String, JSONObject> levels) {
		
		if(levels.containsKey("admin0")) {
			parts.put("admin0", levels.get("admin0").optString("name"));
		}
		if(levels.containsKey("admin1")) {
			parts.put("admin1", levels.get("admin1").optString("name"));
		}
		if(levels.containsKey("admin2")) {
			parts.put("admin2", levels.get("admin2").optString("name"));
		}
		if(levels.containsKey("local_admin")) {
			parts.put("local_admin", levels.get("local_admin").optString("name"));
		}
		if(levels.containsKey("locality")) {
			parts.put("locality", levels.get("locality").optString("name"));
		}
		if(levels.containsKey("neighborhood")) {
			parts.put("neighborhood", levels.get("neighborhood").optString("name"));
		}
		
		if (!fullGeometry) {
			for(Entry<String, JSONObject> entry : levels.entrySet()) {
				entry.getValue().remove("full_geometry");
			}
		}
	}

	private JSONObject getHighway(double lon, double lat) {
		Client client = ESNodeHodel.getClient();
		
		FilteredQueryBuilder q =
				QueryBuilders.filteredQuery(
						QueryBuilders.matchAllQuery(),
						FilterBuilders.andFilter(
								FilterBuilders.termFilter("type", "hghway"),
								FilterBuilders.geoShapeFilter("full_geometry", 
										ShapeBuilder.newCircleBuilder().center(lon, lat)
											.radius(25, DistanceUnit.METERS), ShapeRelation.INTERSECTS)
						));
		
		SearchRequestBuilder searchRequest = client.prepareSearch("gazetteer").setQuery(q);
		searchRequest.setSize(1);
		SearchResponse searchResponse = searchRequest.get();
		
		SearchHit[] hits = searchResponse.getHits().getHits();
		for(SearchHit hit : hits) {
			return new JSONObject(hit.getSource());
		}
		
		return null;
	}

	private JSONObject getPoint(double lon, double lat) {
		Client client = ESNodeHodel.getClient();
		
		FilteredQueryBuilder q =
				QueryBuilders.filteredQuery(
						QueryBuilders.matchAllQuery(),
						FilterBuilders.andFilter(
								FilterBuilders.termsFilter("type", "adrpnt", "poipnt"),
								FilterBuilders.geoDistanceFilter("center_point").point(lat, lon).distance(1000, DistanceUnit.METERS)
						));

		SearchRequestBuilder searchRequest = client.prepareSearch("gazetteer").setQuery(q);
		searchRequest.addSort(SortBuilders.geoDistanceSort("center_point").point(lat, lon));
		searchRequest.setSize(10);
		
		SearchResponse searchResponse = searchRequest.get();
				
		SearchHit[] hits = searchResponse.getHits().getHits();
		
		Point p = GeometryUtils.factory.createPoint(new Coordinate(lon, lat));
		for(SearchHit hit : hits) {
			JSONObject feature = new JSONObject(hit.getSource());
			Geometry geoemtry = GeometryUtils.parseGeometry(feature.optJSONObject("full_geometry"));
			if (geoemtry != null && geoemtry.contains(p)) {
				return feature;
			}
		}
		
		return null;
	}

	private Map<String, JSONObject> getBoundaries(double lon, double lat) {
		Client client = ESNodeHodel.getClient();
		
		GeoShapeFilterBuilder filter = FilterBuilders.geoShapeFilter("full_geometry", 
				ShapeBuilder.newPoint(lon, lat), ShapeRelation.INTERSECTS);
		
		FilteredQueryBuilder q =
				QueryBuilders.filteredQuery(
						QueryBuilders.matchAllQuery(),
						filter);
		
		SearchRequestBuilder searchRequest = client.prepareSearch("gazetteer").setQuery(q);
		
		SearchResponse searchResponse = searchRequest.get();
				
		SearchHit[] hits = searchResponse.getHits().getHits();
		List<JSONObject> boundaries = new ArrayList<JSONObject>();
		
		
		Map<String, JSONObject> levels = new HashMap<String, JSONObject>();
		for(SearchHit hit : hits) {
			JSONObject obj = new JSONObject(hit.getSourceAsString());

			boundaries.add(obj);
			levels.put(obj.optString("addr_level"), obj);
		}
		return levels;
	}
	
}
