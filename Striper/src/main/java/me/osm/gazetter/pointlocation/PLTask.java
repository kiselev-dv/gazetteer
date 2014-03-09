package me.osm.gazetter.pointlocation;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import me.osm.gazetter.striper.GeoJsonWriter;
import me.osm.gazetter.utils.FileUtils;
import me.osm.gazetter.utils.FileUtils.LineHandler;

import org.json.JSONArray;
import org.json.JSONObject;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.index.quadtree.Quadtree;

public class PLTask implements Runnable {
	
	public static interface JointHandler {
		public JSONObject handle(JSONObject addrPoint, List<JSONObject> polygons);
	}
	
	private File src;
	private String[] pointFTypes; 
	private String[] polygonFTypes;

	private final List<JSONObject> addrPoints = new ArrayList<>();
	private final Quadtree addrPointsIndex = new Quadtree();
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
						addrPoints.add(new JSONObject(line));
					}
				}
				
				for(String pf : polygonFTypes) {
					if(line.substring(0, Math.min(25, line.length())).contains(pf)) {
						polygons.add(new JSONObject(line));
					}
				}
			}
			
		});
		
		Collections.sort(addrPoints, BY_ID_COMPARATOR);
		for(JSONObject point : addrPoints) {
			JSONArray ca = point.getJSONObject(GeoJsonWriter.GEOMETRY).getJSONArray(GeoJsonWriter.COORDINATES);
			addrPointsIndex.insert(new Envelope(new Coordinate(ca.getDouble(0), ca.getDouble(1))), point);
		}
		Collections.sort(polygons, BY_ID_COMPARATOR);
		
		join();
		write();
	}

	private void write() {
		//use clar because we will populate list with a same number of lines
		addrPoints.clear();
		
		for(Entry<JSONObject, List<JSONObject>> entry : map.entrySet()) {
			List<JSONObject> boundaries = entry.getValue();
			boundaries.addAll(common);
			
			//copy map key to preserve hash and not to brake hashing
			JSONObject point = (JSONObject) JSONObject.wrap(entry.getKey());
			
			handler.handle(point, boundaries);
			
			addrPoints.add(handler.handle(point, boundaries));
		}
		
		/* XXX: Reafctor with partial source modification
		 * to preserve features which not affected by this
		 * task. 
		 */
		PrintWriter printWriter = null;
		try {
			printWriter = new PrintWriter(src);
			
			for(JSONObject json : addrPoints) {
				printWriter.println(json.toString());
			}
			
			for(JSONObject json : polygons) {
				printWriter.println(json.toString());
			}
			
			printWriter.flush();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			if(printWriter != null) {
				printWriter.close();
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void join() {
		for(JSONObject polygon : polygons) {
			Polygon polyg = getPolygonGeometry(polygon);
			
			Envelope polygonEnvelop = polyg.getEnvelopeInternal();
			for (JSONObject pnt : (List<JSONObject>)addrPointsIndex.query(polygonEnvelop)) {
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
