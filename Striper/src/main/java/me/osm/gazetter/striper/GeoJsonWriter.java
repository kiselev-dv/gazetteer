package me.osm.gazetter.striper;

import java.util.Date;
import java.util.Locale;
import java.util.Map;

import me.osm.gazetter.utils.HilbertCurveHasher;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDateTime;
import org.json.JSONObject;
import org.json.JSONString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;

public class GeoJsonWriter {
	
	private static final String GEOMETRY_TYPE = "type";

	private static final Logger log = LoggerFactory.getLogger(GeoJsonWriter.class.getName());
	
	private static final String TIMESTAMP_PATTERN = "\"" + GeoJsonWriter.TIMESTAMP +  "\":\"";
	private static final String ID_PATTERN = "\"id\":\"";
	private static final String FTYPE_PATTERN = "\"ftype\":\"";
	
	public static final String META = "metainfo";
	public static final String PROPERTIES = "properties";
	public static final String COORDINATES = "coordinates";
	public static final String GEOMETRY = "geometry";
	public static final String ORIGINAL_BBOX = "origBBOX";
	public static final String TIMESTAMP = "timestamp";
	
	private static final DateTimeZone timeZone = DateTimeZone.getDefault();
	
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
	
	private static JSONObject geometryToJSON(Geometry g) {
		if(g instanceof Polygon) {
			JSONObject geomJSON = new JSONObject();
			geomJSON.put(GEOMETRY_TYPE, "Polygon");
			geomJSON.put(COORDINATES, new JsonStringWrapper(asJsonString((Polygon) g)));
			return geomJSON;
		}
		else if (g instanceof LineString) {
			JSONObject geomJSON = new JSONObject();
			geomJSON.put(GEOMETRY_TYPE, "LineString");
			geomJSON.put(COORDINATES, new JsonStringWrapper(asJsonString((LineString) g)));
			return geomJSON;
		}
		else if(g instanceof Point) {
			JSONObject geomJSON = new JSONObject();
			geomJSON.put(GEOMETRY_TYPE, "Point");
			geomJSON.put(COORDINATES, new JsonStringWrapper("[" + 
					String.format(Locale.US, "%.8f", ((Point)g).getX()) + "," + 
					String.format(Locale.US, "%.8f", ((Point)g).getY()) + "]"));
			return geomJSON;
		}
		return null;
	}
	
	public static String featureAsGeoJSON(String id, String type, Map<String, String> attributes, Geometry g, JSONObject meta) {
		
		JSONObject feature = createFeature(id, type, attributes, g, meta);
		addTimestamp(feature);
		
		return feature.toString();
	}

	public static JSONObject createFeature(String id, String type,
			Map<String, String> attributes, Geometry g, JSONObject meta) {
		JSONObject feature = new JSONFeature();
		
		if(id != null)
			feature.put("id", id);
		
		feature.put("ftype", type);
		feature.put(GEOMETRY_TYPE, "Feature");
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
		String mainPart = type + "-" + String.format("%010d", hash) + "-" + 
				meta.getString(GEOMETRY_TYPE).charAt(0) + meta.optLong("id");
		
		int counter = meta.optInt("counter", -1); 
		if(counter >= 0) {
			mainPart += "-" + counter;
		}
		
		return mainPart;
	}

	public static void addTimestamp(JSONObject json) {
		LocalDateTime date = LocalDateTime.now();
		json.put(TIMESTAMP, date.toDateTime(timeZone).toInstant().toString());
	}

	public static Date getTimestamp(String line) {
		int indexOf = line.indexOf(TIMESTAMP_PATTERN);
		if(indexOf >= 0) {
			int begin = indexOf + TIMESTAMP_PATTERN.length();
			int end = line.indexOf("\"", begin);
			String timestampString = line.substring(begin, end);
			try {
				return (new DateTime(timestampString)).toDate();
			} catch (Exception e) {
				log.error("Can't parse timestamp {} for line {}", timestampString, line);
			}
		}
		return null;
	}

	public static String getId(String line) {
		int begin = line.indexOf(ID_PATTERN) + ID_PATTERN.length();
		int end = line.indexOf("\"", begin);
		return line.substring(begin, end);
	}
	
	public static String getFtype(String line) {
		int begin = line.indexOf(FTYPE_PATTERN) + FTYPE_PATTERN.length();
		int end = line.indexOf("\"", begin);
		return line.substring(begin, end);
	}
	
}
