package me.osm.gazetteer.web.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import me.osm.gazetteer.web.ESNodeHodel;
import me.osm.gazetteer.web.api.utils.RequestUtils;
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


/**
 * Inverse geocode API
 * */
public class InverseGeocodeAPI {

	private static final String ALL_LEVEL = "all";
	private static final String OBJECTS_LEVEL = "objects";
	private static final String HIGHWAYS_LEVEL = "highways";

	/**
	 * REST Express routine read method
	 * 
	 * <ol>
	 * <li> Find enclosed features (poi or address)
	 * <li> Sort them, and take one with smallest geometry area as a main
	 * <li> if related set to true, get related features for main feature 
	 * <li> if there is no enclosed feature were found, find nearest highway 
	 * <li> if there is no highway nearby, return boundaries 
	 * </ol>
	 * 
	 * @param request REST Express request
	 * @param response REST Express response
	 * 
	 * @return JSONObject with following structure
	 * */
	public JSONObject read(Request request, Response response){
		
		JSONObject result = new JSONObject();
		
		// Requested point longitude
		double lon = RequestUtils.getDoubleHeader("lon", request);
		
		// Requested point latitude
		double lat = RequestUtils.getDoubleHeader("lat", request);
		
		// Add related objects for founded feature or not
		boolean wRelated = request.getHeader("_related") != null;
		
		// Store full geometry of objects or not
		boolean fullGeometry = request.getHeader(SearchAPI.FULL_GEOMETRY_HEADER) != null 
				&& "true".equals(request.getParameter(SearchAPI.FULL_GEOMETRY_HEADER));
		
		// No more than this amount of neighbours please 
		int maxNeighbours = request.getHeader("max_neighbours") == null ? 15 : 
			Integer.valueOf(request.getHeader("max_neighbours"));
		
		/* How large objects what we are looking for?
		 * 
		 * OBJECTS_LEVEL   try to find enclosing poipnt or adrpoint only
		 * 
		 * HIGHWAYS_LEVEL  if there is no enclosing OBJECTS_LEVEL features,
		 *                 try to find highway
		 *                 
		 * ALL_LEVEL       if there is no highways, return, at least all
		 *                 enclosing boundaries
		 *                 
		 * Why not to include all enclosing boundaries always by default?
		 * All objects already have all enclosing boundaries                               
		 * 
		 */
		String largestLevel = request.getHeader("largest_level") == null ? 
				HIGHWAYS_LEVEL : request.getHeader("largest_level");

		if(largestLevel.equals(HIGHWAYS_LEVEL) && largestLevel.equals(OBJECTS_LEVEL) && largestLevel.equals(ALL_LEVEL)) {
			largestLevel = HIGHWAYS_LEVEL;
		}
		
		if(maxNeighbours > 100) {
			maxNeighbours = 100;
		}
		
		if(maxNeighbours < 0) {
			maxNeighbours = 0;
		}
		
		List<JSONObject> neighbours = maxNeighbours == 0 ? null : new ArrayList<JSONObject>(maxNeighbours);
		List<JSONObject> enclosedFeatures = getEnclosedFeatures(lon, lat, maxNeighbours, neighbours);

		// Get first feature as a main feature.
		JSONObject mainFeature = enclosedFeatures.isEmpty() ? null : enclosedFeatures.remove(0);
		
		// If main feature is founded, write it out
		if(mainFeature != null) {
			return writeMainFeature(wRelated, fullGeometry, neighbours,
					enclosedFeatures, mainFeature);
		}
		
		// Return neighbours only
		if(largestLevel.equals(OBJECTS_LEVEL)) {
			result.put("_neigbours", neighbours);
			return result;
		}

		// If there is no enclosing features, look for highways within 25 meters
		JSONObject highway = getHighway(lon, lat, 25);

		// Address parts to return 
		LinkedHashMap<String, String> parts = new LinkedHashMap<String, String>();
		if(highway != null) {
			
			// Fill address parts by founded highway
			fillByHighway(parts, highway);
			if (!fullGeometry) {
				highway.remove("full_geometry");
			}
			
			result.put("highway", highway);
			result.put("parts", new JSONObject(parts));
			result.put("text", StringUtils.join(parts.values(), ", "));
			
			// Don't forget about neighbours
			result.put("_neigbours", neighbours);
			
			return result;
		}
		else if(largestLevel.equals(HIGHWAYS_LEVEL)) {
			// Don't forget about neighbours
			result.put("_neigbours", neighbours);
			return result;
		}
		
		// Get administrative boundaries 
		Map<String, JSONObject> levels = getBoundariesLevels(lon, lat);
		
		// Fill address parts by founded boundaries
		fillByBoundaries(fullGeometry, parts, levels);
		result.put("boundaries", new JSONObject(levels));
		
		result.put("text", StringUtils.join(parts.values(), ", "));
		result.put("parts", new JSONObject(parts));
		
		// Don't forget about neighbours 
		result.put("_neigbours", neighbours);
		
 		return result;
	}

	/**
	 * Write out founded objects
	 * 
	 * @param find and write related objects {@link FeatureAPI#getRelated}
	 * @param fullGeometry store full geometry for related and neighbour objects
	 * @param neighbours list of neighbours
	 * @param enclosedFeatures list of objects encloses provided point
	 * @param mainFeature most relevant feature
	 * 
	 * @return result encoded as JSONObject
	 * */
	private JSONObject writeMainFeature(boolean wRelated, boolean fullGeometry,
			List<JSONObject> neighbours, List<JSONObject> enclosedFeatures,
			JSONObject mainFeature) {
		
		//Remove full geometry for neighbours
		if(!fullGeometry && neighbours != null) {
			for(JSONObject n : neighbours) {
				n.remove("full_geometry");
			}
		}

		if(wRelated) {
			JSONObject related = FeatureAPI.getRelated(mainFeature);
			if(related != null) {
				mainFeature.put("_related", related);
			}
		}

		if(neighbours != null) {
			mainFeature.put("_neighbours", neighbours);
		}
		
		if(!enclosedFeatures.isEmpty()) {
			mainFeature.put("_enclosed", enclosedFeatures);
		}
		
		return mainFeature;
	}

	/**
	 * Search for highway with r meters around
	 * 
	 * @param lon center longitude
	 * @param lat center latitude
	 * @param r radius in meters
	 * 
	 * @return founded highway or null
	 * */
	public JSONObject getHighway(double lon, double lat, int r) {
		Client client = ESNodeHodel.getClient();
		
		FilteredQueryBuilder q =
				QueryBuilders.filteredQuery(
						QueryBuilders.matchAllQuery(),
						FilterBuilders.andFilter(
								FilterBuilders.termsFilter("type", "hghway", "hghnet"),
								FilterBuilders.geoShapeFilter("full_geometry", 
										ShapeBuilder.newCircleBuilder().center(lon, lat)
											.radius(r, DistanceUnit.METERS), ShapeRelation.INTERSECTS)
						));
		
		SearchRequestBuilder searchRequest = 
				client.prepareSearch("gazetteer").setTypes("location").setQuery(q);
		
		searchRequest.setSize(1);
		SearchResponse searchResponse = searchRequest.get();
		
		SearchHit[] hits = searchResponse.getHits().getHits();
		for(SearchHit hit : hits) {
			return new JSONObject(hit.getSource());
		}
		
		return null;
	}

	/**
	 * Find features, which encloses provided point
	 * 
	 * @param lon longitude
	 * @param lat latitude
	 * @param maxNeighbours maximum amount of neighbour objects
	 * @param neighbours where to put neighbour objects
	 * 
	 * @return enclosed features
	 * */
	public List<JSONObject> getEnclosedFeatures(double lon, double lat, int maxNeighbours, List<JSONObject> neighbours) {
		
		List<JSONObject> result = new ArrayList<>();
		
		SearchRequestBuilder searchRequest = 
				buildEnclosedFeaturesRequest(lon, lat, maxNeighbours);
		
		SearchResponse searchResponse = searchRequest.get();
				
		SearchHit[] hits = searchResponse.getHits().getHits();
		
		Point p = GeometryUtils.factory.createPoint(new Coordinate(lon, lat));
		for(SearchHit hit : hits) {
			JSONObject feature = new JSONObject(hit.getSource());
			Geometry geoemtry = GeometryUtils.parseGeometry(feature.optJSONObject("full_geometry"));
			if (geoemtry != null && geoemtry.contains(p)) {
				
				// save geometry area for futher sorting
				// just not to parse geometry twice
				feature.put("_geometry_area", geoemtry.getArea());
				result.add(feature);
			}
			else if(neighbours != null) {
				
				// Neighbours already sorted by distance from lon,lat by ES
				neighbours.add(feature);
			}
		}

		// This sorting is for the case, when we have building inside POI
		// In such case we assume that building is more important
		Collections.sort(result, new Comparator<JSONObject>(){

			@Override
			public int compare(JSONObject o1, JSONObject o2) {
				return Double.compare(o1.getDouble("_geometry_area"), o2.getDouble("_geometry_area"));
			}
			
		});
		
		return FeatureAPI.mergeFeaturesByID(result);
	}

	/**
	 * Build request for getting features encloses provided lon and lat
	 * 
	 *  @param lon longitude
	 *  @param lat latitude
	 *  @param maxNeighbours how many objects should we check
	 *  
	 *  @return ElasticSearch SearchRequestBuilder
	 * */
	private SearchRequestBuilder buildEnclosedFeaturesRequest(double lon,
			double lat, int maxNeighbours) {
		
		Client client = ESNodeHodel.getClient();
		
		FilteredQueryBuilder q =
				QueryBuilders.filteredQuery(
						QueryBuilders.matchAllQuery(),
						FilterBuilders.andFilter(
								FilterBuilders.termsFilter("type", "adrpnt", "poipnt"),
								FilterBuilders.geoDistanceFilter("center_point").point(lat, lon).distance(1000, DistanceUnit.METERS)
						));

		SearchRequestBuilder searchRequest = client.prepareSearch("gazetteer").setTypes("location").setQuery(q);
		searchRequest.addSort(SortBuilders.geoDistanceSort("center_point").point(lat, lon));
		
		searchRequest.setSize(maxNeighbours == 0 ? 10 : maxNeighbours);
		return searchRequest;
	}

	/**
	 * Get all administrative boundaries encloses provided point
	 * 
	 * @param lon center longitude
	 * @param lat center latitude
	 * 
	 * @return boundaries mapped by it's levels (addr_level attribute value)
	 * */
	public Map<String, JSONObject> getBoundariesLevels(double lon, double lat) {
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
	
	/**
	 * Fill address parts by highway
	 * 
	 * @param parts target parts map
	 * @param highway source of information
	 * */
	private void fillByHighway(LinkedHashMap<String, String> parts, JSONObject highway) {
		if(highway.has("admin0_name")) {
			parts.put("admin0", highway.optString("admin0_name"));
		}
		if(highway.has("admin1_name")) {
			parts.put("admin1", highway.optString("admin1_name"));
		}
		if(highway.has("admin2_name")) {
			parts.put("admin2", highway.optString("admin2_name"));
		}
		if(highway.has("local_admin_name")) {
			parts.put("local_admin", highway.optString("local_admin_name"));
		}
		if(highway.has("locality_name")) {
			parts.put("locality", highway.optString("locality_name"));
		}
		else if(highway.has("nearest_place")) {
			parts.put("locality", highway.getJSONObject("nearest_place").optString("name"));
		}
		if(highway.has("neighborhood_name")) {
			parts.put("neighborhood", highway.optString("neighborhood_name"));
		}
		else if(highway.has("nearest_neighborhood")) {
			parts.put("neighborhood", highway.getJSONObject("nearest_neighborhood").optString("name"));
		}
		if(highway.has("street_name")) {
			parts.put("street", highway.optString("street_name"));
		}
		if(highway.has("housenumber")) {
			parts.put("housenumber", highway.optString("housenumber"));
		}
	}

	/**
	 * Fill address parts by boundaries
	 * 
	 * @param fullGeometry keep full geometry
	 * @param parts target parts map
	 * @param boundaries boundaries mapped by level
	 * */
	private void fillByBoundaries(boolean fullGeometry, LinkedHashMap<String, String> parts,
			Map<String, JSONObject> boundaries) {
		
		if(boundaries.containsKey("admin0")) {
			parts.put("admin0", boundaries.get("admin0").optString("name"));
		}
		if(boundaries.containsKey("admin1")) {
			parts.put("admin1", boundaries.get("admin1").optString("name"));
		}
		if(boundaries.containsKey("admin2")) {
			parts.put("admin2", boundaries.get("admin2").optString("name"));
		}
		if(boundaries.containsKey("local_admin")) {
			parts.put("local_admin", boundaries.get("local_admin").optString("name"));
		}
		if(boundaries.containsKey("locality")) {
			parts.put("locality", boundaries.get("locality").optString("name"));
		}
		if(boundaries.containsKey("neighborhood")) {
			parts.put("neighborhood", boundaries.get("neighborhood").optString("name"));
		}
		
		if (!fullGeometry) {
			for(Entry<String, JSONObject> entry : boundaries.entrySet()) {
				entry.getValue().remove("full_geometry");
			}
		}
	}
	
}
