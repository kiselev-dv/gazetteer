package me.osm.gazetter.pointlocation;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import me.osm.gazetter.utils.FileUtils;
import me.osm.gazetter.utils.FileUtils.LineHandler;

import org.json.JSONArray;
import org.json.JSONObject;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.Polygon;

public class PLTask implements Runnable {
	
	public static interface JointHandler {
		public void handle(JSONObject addrPoint, List<JSONObject> polygons);
	}
	
	private File src;
	private String[] pointFTypes; 
	private String[] polygonFTypes;

	private final List<JSONObject> points = new ArrayList<>();
	private final List<JSONObject> polygons = new ArrayList<>();
	private JointHandler handler;
	private List<JSONObject> common;
	
	private static final GeometryFactory factory = new GeometryFactory();
		
	public PLTask(JointHandler handler, File src, List<JSONObject> common, String[] pointFTypes, String[] polygonFTypes) {
		this.src = src;
		this.pointFTypes = pointFTypes;
		this.polygonFTypes = polygonFTypes;
		this.handler = handler;
		this.common = common;
	}
	
	private static final ByIdComparator BY_ID_COMPARATOR = new ByIdComparator();

	private static final class ByIdComparator implements Comparator<JSONObject>{
		@Override
		public int compare(JSONObject arg0, JSONObject arg1) {
			String id0 = arg0.optString("id");
			String id1 = arg1.optString("id");
			return id0.compareTo(id1);
		}
	} 
	
	private final Map<JSONObject, List<JSONObject>> map = new HashMap<JSONObject, List<JSONObject>>(); 

	@Override
	public void run() {
		
		FileUtils.handleLines(src, new LineHandler() {
			
			@Override
			public void handle(String line) {
				for(String sf : pointFTypes) {
					if(line.substring(0, Math.min(25, line.length())).contains(sf)) {
						points.add(new JSONObject(line));
					}
				}
				
				for(String pf : polygonFTypes) {
					if(line.substring(0, Math.min(25, line.length())).contains(pf)) {
						polygons.add(new JSONObject(line));
					}
				}
			}
			
		});
		
		Collections.sort(points, BY_ID_COMPARATOR);
		Collections.sort(polygons, BY_ID_COMPARATOR);
		
		join();
		write();
	}

	private void write() {
		for(Entry<JSONObject, List<JSONObject>> entry : map.entrySet()) {
			List<JSONObject> boundaries = entry.getValue();
			boundaries.addAll(common);
			JSONObject point = entry.getKey();
			
			handler.handle(point, boundaries);
		}
	}

	private void join() {
		for(JSONObject polygon : polygons) {
			Polygon polyg = getPolygonGeometry(polygon);
			for (JSONObject pnt : points) {
				JSONArray pntg = pnt.getJSONObject("geometry").getJSONArray("coordinates");
				if(polyg.contains(factory.createPoint(new Coordinate(pntg.getDouble(0), pntg.getDouble(1))))){
					if(map.get(pnt) == null) {
						map.put(pnt, new ArrayList<JSONObject>());
					}
					
					map.get(pnt).add(polygon);
				}
			}
		}
	}

	private Polygon getPolygonGeometry(JSONObject polygon) {
		JSONArray coords = polygon.getJSONObject("geometry").getJSONArray("coordinates");
		
		LinearRing shell = null;
		LinearRing[] holes = new LinearRing[coords.length() - 1];
		for(int lineIndex = 0;lineIndex < coords.length(); lineIndex++) {
			JSONArray line = coords.getJSONArray(lineIndex);
			LinearRing lg = getLineGeometry(line);
			if(lineIndex == 0) {
				shell = lg;
			}
			else {
				holes[lineIndex - 1] = lg;
			}
		}
		return factory.createPolygon(shell, holes);
	}

	private LinearRing getLineGeometry(JSONArray line) {
		Coordinate[] coords = new Coordinate[line.length()];
		
		for(int i = 0; i < line.length(); i++) {
			JSONArray p = line.getJSONArray(i);
			coords[i] = new Coordinate(p.getDouble(0), p.getDouble(1));
		}
		
		return factory.createLinearRing(coords);
	}

	
}
