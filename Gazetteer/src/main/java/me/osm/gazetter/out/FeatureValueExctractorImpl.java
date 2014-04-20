package me.osm.gazetter.out;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import me.osm.gazetter.striper.FeatureTypes;
import me.osm.gazetter.striper.GeoJsonWriter;
import me.osm.gazetter.utils.LocatePoint;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;

public class FeatureValueExctractorImpl implements FeatureValueExtractor {

	private static final String NEAREST_NEIGHBOURHOOD_ID = "nearest:neighbourhood.id";
	private static final String NEAREST_NEIGHBOURHOOD = "nearest:neighbourhood";
	private static final String NEAREST_CITY_ID = "nearest:city.id";
	private static final String NEAREST_CITY = "nearest:city";
	private static final String FULL_GEOMETRY = "full-geometry";
	private static final String CENTROID = "centroid";
	private static final String LAT = "lat";
	private static final String LON = "lon";
	private static final String OSM_TYPE = "osm-type";
	private static final String OSM_TYPE_ID = "osm-type-id";
	private static final String OSM_ID = "osm-id";
	private static final String ID = "id";
	private static final String TYPE = "type";
	private static final String TAGS_JSON = "tags.json";
	private static final String TAGS_HSTORE = "tags.hstore";
	private static final String DESCRIPTION = "description";
	private static final String WIKIPEDIA = "wikipedia";
	private static final String NAME = "name";
	private static final String VERBOSE_TYPE = "type-verbose";
	
	@Override
	public Object getValue(String key, JSONObject jsonObject) {
		try {
			String ftype = jsonObject.getString("ftype");
			
			switch (key) {
			case ID:
				return jsonObject.getString(ID);

			case TYPE:
				return ftype;
				
			case OSM_ID:
				return jsonObject.getJSONObject(GeoJsonWriter.META).getLong(ID);
				
			case OSM_TYPE:
				return jsonObject.getJSONObject(GeoJsonWriter.META).getString("type");

			case OSM_TYPE_ID:
				String type = String.valueOf(jsonObject.getJSONObject(GeoJsonWriter.META).getString("type").charAt(0));
				String id = String.valueOf(jsonObject.getJSONObject(GeoJsonWriter.META).getLong(ID));
				return  type + id;
				
			case LON:
				if(FeatureTypes.HIGHWAY_FEATURE_TYPE.equals(ftype)) {
					LineString ls = GeoJsonWriter.getLineStringGeometry(
							jsonObject.getJSONObject(GeoJsonWriter.GEOMETRY)
								.getJSONArray(GeoJsonWriter.COORDINATES));
					Coordinate c = new LocatePoint(ls, 0.5).getPoint();
					return c.x;
				}
				else {
					return jsonObject.getJSONObject(GeoJsonWriter.GEOMETRY)
							.getJSONArray(GeoJsonWriter.COORDINATES).getDouble(0);
				}

			case LAT:
				if(FeatureTypes.HIGHWAY_FEATURE_TYPE.equals(ftype)) {
					LineString ls = GeoJsonWriter.getLineStringGeometry(
							jsonObject.getJSONObject(GeoJsonWriter.GEOMETRY)
								.getJSONArray(GeoJsonWriter.COORDINATES));
					Coordinate c = new LocatePoint(ls, 0.5).getPoint();
					return c.y;
				}
				else {
					return jsonObject.getJSONObject(GeoJsonWriter.GEOMETRY)
							.getJSONArray(GeoJsonWriter.COORDINATES).getDouble(1);
				}
			
			case CENTROID:
				if(FeatureTypes.HIGHWAY_FEATURE_TYPE.equals(ftype)) {
					LineString ls = GeoJsonWriter.getLineStringGeometry(
							jsonObject.getJSONObject(GeoJsonWriter.GEOMETRY)
								.getJSONArray(GeoJsonWriter.COORDINATES));
					Coordinate c = new LocatePoint(ls, 0.5).getPoint();
					return "POINT (" + c.x + " " + c.y + ")";
				}
				else {
					JSONArray coords = jsonObject.getJSONObject(GeoJsonWriter.GEOMETRY)
							.getJSONArray(GeoJsonWriter.COORDINATES);
					
					return "POINT (" + coords.getDouble(0) + " " + coords.getDouble(1) + ")";
				}

			case FULL_GEOMETRY:
				
				JSONObject fullGeometry = null;
				
				if(FeatureTypes.PLACE_POINT_FTYPE.equals(ftype)) {
					JSONObject matchedBoundary = jsonObject.optJSONObject("matchedBoundary");
					if(matchedBoundary != null) {
						fullGeometry = matchedBoundary.getJSONObject(GeoJsonWriter.META).optJSONObject(GeoJsonWriter.FULL_GEOMETRY);
					}
				}
				else {
					JSONObject meta = jsonObject.getJSONObject(GeoJsonWriter.META);
					fullGeometry = meta.optJSONObject("fullGeometry");
				}
				
				if(fullGeometry != null && "MultiPolygon".equals(fullGeometry.optString("type"))) {
					MultiPolygon polygon = GeoJsonWriter.getMultiPolygonGeometry(fullGeometry.getJSONArray("coordinates"));
					return polygon.toString();
				}
				if(fullGeometry != null && "Polygon".equals(fullGeometry.optString("type"))) {
					Polygon polygon = GeoJsonWriter.getPolygonGeometry(fullGeometry.getJSONArray("coordinates"));
					return polygon.toString();
				}
				else if(fullGeometry != null && "LineString".equals(fullGeometry.optString("type"))) {
					LineString ls = GeoJsonWriter.getLineStringGeometry(fullGeometry.getJSONArray("coordinates"));
					return ls.toString();
				}
				
				break;
				
			case NEAREST_CITY:
				return jsonObject.getJSONObject("nearestCity").getJSONObject(GeoJsonWriter.PROPERTIES).optString("name");
				
			case NEAREST_CITY_ID:
				return jsonObject.getJSONObject("nearestCity").getJSONObject(GeoJsonWriter.PROPERTIES).optString(ID);
				
			case NEAREST_NEIGHBOURHOOD:
				return jsonObject.getJSONObject("nearestNeighbour").getJSONObject(GeoJsonWriter.PROPERTIES).optString("name");
			
			case NEAREST_NEIGHBOURHOOD_ID:	
				return jsonObject.getJSONObject("nearestNeighbour").getJSONObject(GeoJsonWriter.PROPERTIES).optString(ID);
				
			case TAGS_JSON:
				return jsonObject.getJSONObject(GeoJsonWriter.PROPERTIES).toString();

			case TAGS_HSTORE:
				return asHStore(jsonObject.getJSONObject(GeoJsonWriter.PROPERTIES));
				
			case DESCRIPTION:
				return jsonObject.getJSONObject(GeoJsonWriter.PROPERTIES).optString("description");

			case WIKIPEDIA:
				return jsonObject.getJSONObject(GeoJsonWriter.PROPERTIES).optString("wikipedia");

			case VERBOSE_TYPE:
				if(FeatureTypes.HIGHWAY_FEATURE_TYPE.equals(ftype) 
						|| FeatureTypes.POI_FTYPE.equals(ftype)
						|| FeatureTypes.ADDR_POINT_FTYPE.equals(ftype)) {
					return ftype;
				}
				else if (FeatureTypes.PLACE_POINT_FTYPE.equals(ftype)) {
					String place = jsonObject.getJSONObject(GeoJsonWriter.PROPERTIES).optString("place");
					if(StringUtils.isNoneBlank(place)) {
						return "place:" + place;
					}

					String boundary = jsonObject.getJSONObject(GeoJsonWriter.PROPERTIES).optString("admin_level");
					if(StringUtils.isNoneBlank(boundary)) {
						return "boundary:" + boundary;
					}
					
				}
				return null;

			}
		}
		catch (Exception e) {
			return null;
		}
		
		if(key.equals(NAME)) {
			return jsonObject.getJSONObject(GeoJsonWriter.PROPERTIES).optString("name", null);
		}
		else if(key.contains(NAME)) {
			return jsonObject.getJSONObject(GeoJsonWriter.PROPERTIES).optString(key, null);
		}
		
		return null;
	}

	@SuppressWarnings("unchecked")
	public static String asHStore(JSONObject jsonObject) {
		
		List<String> pairs = new ArrayList<>();
		
		for(String key : (Collection<String>)jsonObject.keySet()) {
			String val = jsonObject.get(key).toString();
			pairs.add("\"" + key + "\"=>\"" + val + "\"");
		}
		
		return StringUtils.join(pairs, ",");
	}

	public static String asHStore(Map<String, String> tags) {
		
		List<String> pairs = new ArrayList<>();
		
		for(Entry<String, String> tag : tags.entrySet()) {
			pairs.add("\"" + escape4Hstore(tag.getKey()) + "\"=>\"" + escape4Hstore(tag.getValue()) + "\"");
		}
		
		return StringUtils.join(pairs, ",");
	}

	private static String escape4Hstore(String string) {
		return StringUtils.replace(string, "$", "&#36;");
	}

	@Override
	public Collection<String> getSupportedKeys() {
		return Arrays.asList(ID, TYPE, OSM_ID, OSM_TYPE, LON, LAT,
				CENTROID, FULL_GEOMETRY, NEAREST_CITY, NEAREST_CITY_ID,
				NEAREST_NEIGHBOURHOOD, NEAREST_NEIGHBOURHOOD_ID,
				DESCRIPTION, WIKIPEDIA, TAGS_JSON, TAGS_HSTORE, OSM_TYPE_ID, 
				VERBOSE_TYPE);
	}
	
	
}
