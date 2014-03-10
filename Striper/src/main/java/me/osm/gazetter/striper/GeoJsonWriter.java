package me.osm.gazetter.striper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import me.osm.gazetter.utils.HilbertCurveHasher;

import org.json.JSONObject;
import org.json.JSONString;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;

public class GeoJsonWriter {
	
	public static final String META = "metainfo";
	public static final String PROPERTIES = "properties";
	public static final String COORDINATES = "coordinates";
	public static final String GEOMETRY = "geometry";
	public static final String META_SLICES = "slices";
	
	private static final class JsonStringWrapper implements JSONString {

		private String s;
		public JsonStringWrapper(String s) {
			this.s = s;
		}
		
		@Override
		public String toJSONString() {
			return this.s;
		}
		
	}
	
	public static final class JSONFeature extends JSONObject {
		
		@Override
		@SuppressWarnings({ "unchecked", "rawtypes" })
		public Iterator keys() {
			
			List<String> keys = new ArrayList<String>(keySet());
			Collections.sort(keys, new Comparator<String>() {
				@Override
				public int compare(String o1, String o2) {
					int i1 = "id".equals(o1) ? 0 : "ftype".equals(o1) ? 1 : 10; 
					int i2 = "id".equals(o2) ? 0 : "ftype".equals(o2) ? 1 : 10; 
					
					return i1 - i2;
				}
			});
			
			return keys.iterator();
		}
	}
	
	private static JSONObject geometryToJSON(Geometry g) {
		if(g instanceof Polygon) {
			JSONObject geomJSON = new JSONObject();
			geomJSON.put("type", "Polygon");
			geomJSON.put(COORDINATES, new JsonStringWrapper(asJsonString((Polygon) g)));
			return geomJSON;
		}
		else if(g instanceof Point) {
			JSONObject geomJSON = new JSONObject();
			geomJSON.put("type", "Point");
			geomJSON.put(COORDINATES, new JsonStringWrapper("[" + 
					String.format(Locale.US, "%.8f", ((Point)g).getX()) + "," + 
					String.format(Locale.US, "%.8f", ((Point)g).getY()) + "]"));
			return geomJSON;
		}
		return null;
	}
	
	public static String featureAsGeoJSON(String id, String type, Map<String, String> attributes, Geometry g, JSONObject meta) {
		
		JSONObject feature = createFeature(id, type, attributes, g, meta);
		
		return feature.toString();
	}

	public static JSONObject createFeature(String id, String type,
			Map<String, String> attributes, Geometry g, JSONObject meta) {
		JSONObject feature = new JSONFeature();
		
		if(id != null)
			feature.put("id", id);
		
		feature.put("ftype", type);
		feature.put("type", "Feature");
		feature.put(GEOMETRY, geometryToJSON(g));
		feature.put(PROPERTIES, attributes);
		feature.put(META, meta);
		return feature;
	}
	
	

	private static String asJsonString(Polygon polygon) {
		StringBuilder rings = new StringBuilder();
		
		rings.append(",").append(asJsonString(polygon.getExteriorRing()));
		
		for(int i=0; i < polygon.getNumInteriorRing(); i++) {
			rings.append(",").append(asJsonString(polygon.getInteriorRingN(i)));
		}
		
		return "[" + rings.substring(1) + "]";
	}

	private static String asJsonString(LineString ring) {
		StringBuilder sb = new StringBuilder();
		
		for(Coordinate c : ring.getCoordinates()) {
			sb.append(",[").append(String.format(Locale.US, "%.8f", c.x)).append(",").append(String.format(Locale.US, "%.8f", c.y)).append("]");
		}
		
		return "[" + sb.substring(1) + "]";
	}
	
	public static String getId(String type, Point point, JSONObject meta) {
		long hash = HilbertCurveHasher.encode(point.getX(), point.getY());
		return type + "-" + String.format("%010d", hash) + "-" + meta.getString("type").charAt(0) + meta.optLong("id");
	}

}
