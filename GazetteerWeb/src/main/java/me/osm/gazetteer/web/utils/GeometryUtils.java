package me.osm.gazetteer.web.utils;

import org.json.JSONArray;
import org.json.JSONObject;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;

public class GeometryUtils {
	
	public static final GeometryFactory factory = new GeometryFactory();
	
	public static Geometry parseGeometry(JSONObject geom) {
		if(geom != null) {
			String type = geom.optString("type").toLowerCase();
			JSONArray coords = geom.getJSONArray("coordinates");
			switch (type) {
			case "point":
				return factory.createPoint(new Coordinate(coords.getDouble(0), coords.getDouble(1)));
			case "linestring":
				return getLineStringGeometry(coords);
			case "polygon":
				return getPolygonGeometry(coords);
			case "multipolygon":
				return getMultiPolygonGeometry(coords);
			}
		}
		
		return null;
	}
	
	public static MultiPolygon getMultiPolygonGeometry(JSONArray polygon) {
		
		Polygon polygons[] = new Polygon[polygon.length()];
		for(int i = 0; i < polygon.length(); i++) {
			polygons[i] = getPolygonGeometry(polygon.getJSONArray(i));
		}
		
		return factory.createMultiPolygon(polygons);
	}

	public static Polygon getPolygonGeometry(JSONArray coords) {
		LinearRing shell = null;
		LinearRing[] holes = new LinearRing[coords.length() - 1];
		for(int lineIndex = 0;lineIndex < coords.length(); lineIndex++) {
			JSONArray line = coords.getJSONArray(lineIndex);
			LinearRing lg = getLinearRingGeometry(line);
			if(lineIndex == 0) {
				shell = lg;
			}
			else {
				holes[lineIndex - 1] = lg;
			}
		}
		return factory.createPolygon(shell, holes);
	}

	public static LinearRing getLinearRingGeometry(JSONArray line) {
		Coordinate[] coords = new Coordinate[line.length()];
		
		for(int i = 0; i < line.length(); i++) {
			JSONArray p = line.getJSONArray(i);
			coords[i] = new Coordinate(p.getDouble(0), p.getDouble(1));
		}
		
		return factory.createLinearRing(coords);
	}

	public static LineString getLineStringGeometry(JSONArray coordsJSON) {
		Coordinate[] coords = new Coordinate[coordsJSON.length()];
		
		for(int i = 0; i < coordsJSON.length(); i++) {
			JSONArray p = coordsJSON.getJSONArray(i);
			coords[i] = new Coordinate(p.getDouble(0), p.getDouble(1));
		}
		
		return factory.createLineString(coords);
	}
}
