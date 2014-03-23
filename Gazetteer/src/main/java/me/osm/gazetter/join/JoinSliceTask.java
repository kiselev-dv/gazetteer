package me.osm.gazetter.join;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import me.osm.gazetter.addresses.AddressesParser;
import me.osm.gazetter.striper.FeatureTypes;
import me.osm.gazetter.striper.GeoJsonWriter;
import me.osm.gazetter.striper.JSONFeature;
import me.osm.gazetter.utils.FileUtils;
import me.osm.gazetter.utils.FileUtils.LineHandler;

import org.json.JSONArray;
import org.json.JSONObject;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.index.quadtree.Quadtree;
import com.vividsolutions.jts.operation.buffer.BufferOp;

public class JoinSliceTask implements Runnable {
	
	private static final double BUFFER_DISTANCE = 1.0 / 111195.0 * 250;
	
	private File src;
	
	private final List<JSONObject> addrPoints = new ArrayList<>();
	private final Quadtree addrPointsIndex = new Quadtree();
	private final Quadtree streetsPointsIndex = new Quadtree();
	
	private final List<JSONObject> boundaries = new ArrayList<>();
	private final List<JSONObject> placesVoronoi = new ArrayList<>();
	private final List<JSONObject> neighboursVoronoi = new ArrayList<>();
	private final List<JSONObject> streets = new ArrayList<>();

	private AddrJointHandler handler;
	private List<JSONObject> common;
	
	private static final GeometryFactory factory = new GeometryFactory();
		
	public JoinSliceTask(AddrJointHandler handler, File src, List<JSONObject> common) {
		this.src = src;
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
	
	private final Map<JSONObject, List<JSONObject>> addr2streets = new HashMap<JSONObject, List<JSONObject>>(); 
	private final Map<JSONObject, List<JSONObject>> addr2bndries = new HashMap<JSONObject, List<JSONObject>>(); 
	private final Map<JSONObject, Set<JSONObject>> street2bndries = new HashMap<JSONObject, Set<JSONObject>>(); 
	private final Map<JSONObject, JSONObject> addr2PlaceVoronoy = new HashMap<>(); 
	private final Map<JSONObject, JSONObject> addr2NeighbourVoronoy = new HashMap<>(); 

	@Override
	public void run() {
		
		FileUtils.handleLines(src, new LineHandler() {
			
			@Override
			public void handle(String line) {
				String ftype = GeoJsonWriter.getFtype(line);
				
				if(FeatureTypes.ADDR_POINT_FTYPE.equals(ftype)) {
					addrPoints.add(new JSONObject(line));
				}

				else if(FeatureTypes.ADMIN_BOUNDARY_FTYPE.equals(ftype) 
						|| FeatureTypes.PLACE_BOUNDARY_FTYPE.equals(ftype)) {
					boundaries.add(new JSONObject(line));
				}
				
				else if(FeatureTypes.NEIGHBOUR_DELONEY_FTYPE.equals(ftype)) {
					neighboursVoronoi.add(new JSONObject(line));
				}

				else if(FeatureTypes.PLACE_DELONEY_FTYPE.equals(ftype)) {
					placesVoronoi.add(new JSONObject(line));
				}

				else if(FeatureTypes.HIGHWAY_FEATURE_TYPE.equals(ftype)) {
					streets.add(new JSONObject(line));
				}
			}
			
		});
		
		Collections.sort(addrPoints, BY_ID_COMPARATOR);
		for(JSONObject point : addrPoints) {
			JSONArray ca = point.getJSONObject(GeoJsonWriter.GEOMETRY).getJSONArray(GeoJsonWriter.COORDINATES);
			addrPointsIndex.insert(new Envelope(new Coordinate(ca.getDouble(0), ca.getDouble(1))), point);
		}
		Collections.sort(boundaries, BY_ID_COMPARATOR);
		
		for(JSONObject street : streets) {
			JSONArray ca = street.getJSONObject(GeoJsonWriter.GEOMETRY).getJSONArray(GeoJsonWriter.COORDINATES);
			for(int i =0; i < ca.length(); i++) {
				JSONArray p = ca.getJSONArray(i);
				Coordinate coordinate = new Coordinate(p.getDouble(0), p.getDouble(1));
				streetsPointsIndex.insert(new Envelope(coordinate), new Object[]{coordinate, street});
			}
		}
		
		join();
		
		write();
	}

	private void write() {
		//use clear because we will populate list with a same number of lines
		addrPoints.clear();
		
		for(Entry<JSONObject, List<JSONObject>> entry : addr2bndries.entrySet()) {
			List<JSONObject> boundaries = entry.getValue();
			boundaries.addAll(common);
			
			addrPoints.add(handler.handle(
					entry.getKey(), 
					boundaries, 
					addr2streets.get(entry.getKey()),
					addr2PlaceVoronoy.get(entry.getKey()), 
					addr2NeighbourVoronoy.get(entry.getKey()))
			);
		}
		
		PrintWriter printWriter = null;
		try {
			printWriter = new PrintWriter(new FileOutputStream(src, true));
			
			for(JSONObject json : addrPoints) {
				GeoJsonWriter.addTimestamp(json);
				String geoJSONString = new JSONFeature(json).toString();
				
				assert GeoJsonWriter.getId(geoJSONString).equals(json.optString("id")) 
					: "Failed getId for " + geoJSONString;

				assert GeoJsonWriter.getFtype(geoJSONString).equals(FeatureTypes.ADDR_POINT_FTYPE) 
					: "Failed getFtype for " + geoJSONString;
				
				printWriter.println(geoJSONString);
			}
			
			for(Entry<JSONObject, Set<JSONObject>> entry : street2bndries.entrySet()) {
				List<JSONObject> boundaries = new ArrayList<>(entry.getValue());
				boundaries.addAll(common);
				JSONObject street = entry.getKey();
				//TODO split in case of two or more parts with same lvl
				street.put("boundaries", AddressesParser.boundariesAsArray(boundaries));
				GeoJsonWriter.addTimestamp(street);
				
				String geoJSONString = new JSONFeature(street).toString();
				
				assert GeoJsonWriter.getId(geoJSONString).equals(street.optString("id")) 
					: "Failed getId for " + geoJSONString;

				assert GeoJsonWriter.getFtype(geoJSONString).equals(FeatureTypes.HIGHWAY_FEATURE_TYPE) 
					: "Failed getFtype for " + geoJSONString;
				
				printWriter.println(geoJSONString);
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

	private void join() {
		for(JSONObject boundary : boundaries) {
			Polygon polygon = getPolygonGeometry(boundary);
			
			many2ManyJoin(boundary, polygon, addr2bndries, addrPointsIndex);
			many2ManyHashJoin(boundary, polygon, street2bndries, streetsPointsIndex);
		}
		
		for (JSONObject strtJSON : streets) {
			LineString ls = getLineStringGeometry(strtJSON);
			
			Geometry buffer = ls.buffer(BUFFER_DISTANCE, 2, BufferOp.CAP_ROUND);
			if(buffer instanceof Polygon) {
				many2ManyJoin(strtJSON, (Polygon) buffer, addr2streets, addrPointsIndex);
			}
			else if(buffer instanceof MultiPolygon) {
				for(int i = 0; i < buffer.getNumGeometries(); i++) {
					Polygon p = (Polygon) buffer.getGeometryN(i);
					if(p.isValid()) {
						many2ManyJoin(strtJSON, p, addr2streets, addrPointsIndex);
					}
				}
			}
		}
		
		one2OneJoin(placesVoronoi, addr2PlaceVoronoy);
		one2OneJoin(neighboursVoronoi, addr2NeighbourVoronoy);
	}

	private void many2ManyJoin(JSONObject object, Polygon polyg, Map<JSONObject, List<JSONObject>> result, Quadtree index) {
		Envelope polygonEnvelop = polyg.getEnvelopeInternal();
		for (Object entry : index.query(polygonEnvelop)) {
			
			JSONArray pntg = ((JSONObject)entry).getJSONObject(GeoJsonWriter.GEOMETRY).getJSONArray(GeoJsonWriter.COORDINATES);
			Coordinate pnt = new Coordinate(pntg.getDouble(0), pntg.getDouble(1));;
			JSONObject obj = (JSONObject) entry;
			
			if(polyg.intersects(factory.createPoint(pnt))) {
				if(result.get(obj) == null) {
					result.put(obj, new ArrayList<JSONObject>());
				}
				
				result.get(obj).add(object);
			}
		}
	}

	private void many2ManyHashJoin(JSONObject object, Polygon polyg, Map<JSONObject, Set<JSONObject>> result, Quadtree index) {
		Envelope polygonEnvelop = polyg.getEnvelopeInternal();
		for (Object entry : index.query(polygonEnvelop)) {
			
			Coordinate pnt = (Coordinate) ((Object[])entry)[0];
			JSONObject obj = (JSONObject) ((Object[])entry)[1];
			
			if(polyg.intersects(factory.createPoint(pnt))) {
				if(result.get(obj) == null) {
					result.put(obj, new HashSet<JSONObject>());
				}
				
				result.get(obj).add(object);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void one2OneJoin(List<JSONObject> polygons, Map<JSONObject, JSONObject> result) {
		for(JSONObject placeV : polygons) {
			Polygon polyg = getPolygonGeometry(placeV);
			Envelope polygonEnvelop = polyg.getEnvelopeInternal();
			for (JSONObject pnt : (List<JSONObject>)addrPointsIndex.query(polygonEnvelop)) {
				JSONArray pntg = pnt.getJSONObject(GeoJsonWriter.GEOMETRY).getJSONArray(GeoJsonWriter.COORDINATES);
				if(polyg.contains(factory.createPoint(new Coordinate(pntg.getDouble(0), pntg.getDouble(1))))){
					result.put(pnt, placeV);
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

	private LinearRing getLinearRingGeometry(JSONArray line) {
		Coordinate[] coords = new Coordinate[line.length()];
		
		for(int i = 0; i < line.length(); i++) {
			JSONArray p = line.getJSONArray(i);
			coords[i] = new Coordinate(p.getDouble(0), p.getDouble(1));
		}
		
		return factory.createLinearRing(coords);
	}

	private LineString getLineStringGeometry(JSONObject strtJSON) {
		JSONArray coordsJSON = strtJSON.getJSONObject("geometry").getJSONArray("coordinates");
		Coordinate[] coords = new Coordinate[coordsJSON.length()];
		
		for(int i = 0; i < coordsJSON.length(); i++) {
			JSONArray p = coordsJSON.getJSONArray(i);
			coords[i] = new Coordinate(p.getDouble(0), p.getDouble(1));
		}
		
		return factory.createLineString(coords);
	}
	
}
