package me.osm.gazetter.striper.builders;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import me.osm.gazetter.striper.FeatureTypes;
import me.osm.gazetter.striper.GeoJsonWriter;
import me.osm.gazetter.striper.Slicer;
import me.osm.gazetter.striper.readers.PointsReader.Node;
import me.osm.gazetter.striper.readers.RelationsReader.Relation;
import me.osm.gazetter.striper.readers.WaysReader.Way;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONString;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.geom.util.GeometryEditor;
import com.vividsolutions.jts.index.quadtree.Quadtree;
import com.vividsolutions.jts.triangulate.VoronoiDiagramBuilder;
import com.vividsolutions.jts.triangulate.quadedge.QuadEdgeSubdivision;

public class PlacePointsBuilder extends ABuilder {
	
	public static interface PlacePointHandler {
		public void handlePlacePoint(Map<String, String> tags, Point pnt,
				JSONObject meta);
	}
	
	private static GeometryFactory fatory = new GeometryFactory();
	private Slicer slicer;
	
	private static Map<Coordinate, JSONObject> cityes = new HashMap<>(); 
	private static Map<Coordinate, JSONObject> neighbours = new HashMap<>(); 
	
	public static class BBOX {
		double minX = Double.NaN;
		double minY = Double.NaN;

		double maxX = Double.NaN;
		double maxY = Double.NaN;
		
		public void extend(double x, double y) {
			if(Double.isNaN(minX)) {
				minX = x;
				maxX = x;
				minY = y;
				maxY = y;
			}
			else {
				minX = Math.min(minX, x);
				maxX = Math.max(maxX, x);
				
				minY = Math.min(minY, y);
				maxY = Math.max(maxY, y);
			}
		}

		public double getDX() {
			return maxX - minX;
		}
	}
	
	private static final BBOX originalBBOX = new BBOX(); 
	private static final BBOX translatedBBOX = new BBOX(); 
	
	//this tricky flag will mess everything around 180*
	private static volatile boolean weAreInRussia = false; 
	
	private static final Set<String> files = new HashSet<>();
	
	private static final Set<String> PLACE_CITY = new HashSet<String>(Arrays.asList(new String[]{
			"city",
			"town",
			"hamlet",
			"village",
			"isolated_dwelling"
	}));

	private static final Set<String> PLACE_NEIGHBOUR = new HashSet<String>(Arrays.asList(new String[]{
			"suburb",
			"neighbourhood",
			"quarter"
	}));
	
	private static final double DEGREE_OFFSET = -90.0;
	
	public static final double moveTo(double x) {
		if(x < DEGREE_OFFSET) {
			return x + 360 + DEGREE_OFFSET;
		}
		return x + DEGREE_OFFSET;
	}

	public static final double moveBack(double x) {
		if(x > -DEGREE_OFFSET) {
			return x - 360 - DEGREE_OFFSET;
		}
		return x - DEGREE_OFFSET;
	}

	public PlacePointsBuilder(Slicer slicer) {
		this.slicer = slicer;
	}

	@Override
	public void handle(Relation rel) {

	}

	@Override
	public void handle(Way line) {

	}

	@Override
	public void handle(Node node) {
		if(node.tags.containsKey("place")) {
			Coordinate coordinate = new Coordinate(node.lon, node.lat);
			Point pnt = fatory.createPoint(coordinate);
			
			JSONObject meta = new JSONObject();
			meta.put("type", "node");
			meta.put("id", node.id);
			
			slicer.handlePlacePoint(node.tags, pnt, meta);
			
			originalBBOX.extend(node.lon, node.lat);
			translatedBBOX.extend(moveTo(node.lon), node.lat);
			
			String id = GeoJsonWriter.getId(FeatureTypes.PLACE_DELONEY_FTYPE, pnt, meta);
			JSONObject feature = GeoJsonWriter.createFeature(id, FeatureTypes.PLACE_DELONEY_FTYPE, node.tags, pnt, meta);
			
			if(PLACE_CITY.contains(node.tags.get("place"))) {
				cityes.put(getCoordinateFromGJSON(feature), feature);
				files.add(Slicer.getFilePrefix(node.lon));
			}
			
			if(PLACE_NEIGHBOUR.contains(node.tags.get("place"))) {
				neighbours.put(getCoordinateFromGJSON(feature), feature);
				files.add(Slicer.getFilePrefix(node.lon));
			}
		}
		
		if(node.tags.containsKey("addr:housenumber")) {
			originalBBOX.extend(node.lon, node.lat);
			translatedBBOX.extend(moveTo(node.lon), node.lat);
		}
	}
	
	@Override
	public void afterLastRun() {

		// Possibly we processing Russia. 
		// And we have wrong originalBBOX which covers whole planet.
		// So lets translate all coordinates for Vronoi diagramm and 
		// move it back while writing
		if(originalBBOX.getDX() > translatedBBOX.getDX() + 0.0001) {
			weAreInRussia = true;
			System.err.println("Warn: we are in Russia!");
			
			Map<Coordinate, JSONObject> russianCityes = new HashMap<>();
			for(Entry<Coordinate, JSONObject> entry : cityes.entrySet()) {
				Coordinate c = entry.getKey();
				c.x = moveTo(c.x); 
				russianCityes.put(c, entry.getValue());
			}
			cityes = russianCityes;

			Map<Coordinate, JSONObject> russianNeighbours = new HashMap<>();
			for(Entry<Coordinate, JSONObject> entry : neighbours.entrySet()) {
				Coordinate c = entry.getKey();
				c.x = moveTo(c.x); 
				russianNeighbours.put(c, entry.getValue());
			}
			neighbours = russianNeighbours;
		}
		
		VoronoiDiagramBuilder cvb = new VoronoiDiagramBuilder();
		
		Quadtree neighboursQT = new Quadtree();
		for (Entry<Coordinate, JSONObject> entry : neighbours.entrySet()) {
			neighboursQT.insert(new Envelope(entry.getKey()), entry.getValue());
		}
		
		cvb.setSites(cityes.keySet());
		
		BBOX bbox = weAreInRussia ? translatedBBOX : originalBBOX;
		cvb.setClipEnvelope(new Envelope(bbox.minX, bbox.maxX, bbox.minY, bbox.maxY));
		
		QuadEdgeSubdivision subdivision = cvb.getSubdivision();
		
		@SuppressWarnings("unchecked")
		Collection<Polygon> cityVoronoiPolygons = subdivision.getVoronoiCellPolygons(fatory);
		
		for(Polygon cityPolygon : cityVoronoiPolygons) {
			JSONObject cityJSON = cityes.get((Coordinate)cityPolygon.getUserData()); 
			handleCityVoronoy(cityJSON, cityPolygon, neighboursQT);
		}
		
	}

	private static Coordinate getCoordinateFromGJSON(JSONObject gjson) {
		Object pc = gjson.getJSONObject(GeoJsonWriter.GEOMETRY).get(GeoJsonWriter.COORDINATES);
		
		if(pc instanceof JSONArray) {
			return new Coordinate(((JSONArray)pc).getDouble(0), ((JSONArray)pc).getDouble(1));
		}
		else if(pc instanceof JSONString) {
			String[] split = StringUtils.split(((JSONString) pc).toJSONString(), ",[]");
			return new Coordinate(Double.parseDouble(split[0]), Double.parseDouble(split[1]));
		}

		return null;
	}

	/**
	 * placeFeature - original coordinates
	 * cityPolygon - translated coordinates
	 * neighboursQT - translated coordinates
	 * */
	private void handleCityVoronoy(JSONObject placeFeature, Polygon cityPolygon, Quadtree neighboursQT) {
		
		Polygon originalCityPolygon = weAreInRussia ? movePolygonBack(cityPolygon) : cityPolygon;
		
		//original coords
		JSONObject rfeature = mergeDeloneyCenter(placeFeature, originalCityPolygon, FeatureTypes.PLACE_DELONEY_FTYPE);
		String rstring = rfeature.toString();
		
		//original coordinates
		writePolygonToExistFiles(originalCityPolygon, rstring);
		
		buildNighboursVoronoiPolygons(cityPolygon, neighboursQT, rfeature);
	}

	/**
	 * cityPolygon - translated coordinates
	 * neighboursQT - translated coordinates
	 * cityFeature - originalCoords
	 * */
	private void buildNighboursVoronoiPolygons(Polygon cityPolygon,
			Quadtree neighboursQT, JSONObject cityFeature) {
		
		//translated coords
		List<Coordinate> neighboursCoords = new ArrayList<>();
		
		//translated coords
		Envelope cityPolygonEnv = cityPolygon.getEnvelopeInternal();
		
		@SuppressWarnings("unchecked")
		List<JSONObject> neighbourCandidates = neighboursQT.query(cityPolygonEnv);
		
		for(JSONObject neighbour : neighbourCandidates) {
			
			//original coordinates
			Coordinate coordinate = getCoordinateFromGJSON(neighbour);
			
			if(weAreInRussia) {
				coordinate.x = moveTo(coordinate.x);
			}
			
			if(cityPolygon.contains(fatory.createPoint(coordinate))) {
				neighboursCoords.add(coordinate);
			}
		}
		
		if(!neighboursCoords.isEmpty()) {
			VoronoiDiagramBuilder nvb = new VoronoiDiagramBuilder();
			
			nvb.setSites(neighboursCoords);
			nvb.setClipEnvelope(cityPolygonEnv);
			
			@SuppressWarnings({ "unchecked" })
			List<Polygon> neighboursPolygons = nvb.getSubdivision().getVoronoiCellPolygons(fatory);
			
			for(Polygon neighbourPolygon : neighboursPolygons) {
				
				Polygon intersection = (Polygon)neighbourPolygon.intersection(cityPolygon);
				if(!intersection.isEmpty()) {
					JSONObject neighbour = neighbours.get(neighbourPolygon.getUserData()); 
					handleNeighbour(intersection, cityFeature, neighbour);
				}
			}
		}
	}
	
	/**
	 * neighbourPolygon - translated coordinates
	 * cityFeature - original coordinates 
	 * neighbourFeature - original coordinates
	 * */
	private void handleNeighbour(Polygon neighbourPolygon, JSONObject cityFeature,
			JSONObject neighbourFeature) {
		
		if(weAreInRussia) {
			neighbourPolygon = movePolygonBack(neighbourPolygon);
		}
		
		JSONObject rfeature = mergeDeloneyCenter(neighbourFeature, neighbourPolygon, FeatureTypes.NEIGHBOUR_DELONEY_FTYPE);
		rfeature.put("cityID", cityFeature.getString("id"));
		String rstring = rfeature.toString();
		
		writePolygonToExistFiles(neighbourPolygon, rstring);
		
	}

	private void writePolygonToExistFiles(Polygon polygon,
			String rstring) {
		
		Envelope env = polygon.getEnvelopeInternal();
		
		//TODO: handle 180*
		double minX = env.getMinX();
		double maxX = env.getMaxX();
		
		double dx = Math.abs(maxX - minX);
		double dxt = Math.abs(moveTo(maxX) - moveTo(minX));

		if(dxt < dx) {
			
			int from = (new Double((-180.0 + 180.0) * 10.0).intValue());
			int to = (new Double((minX + 180.0) * 10.0).intValue());
			writeToExistFiles(rstring, from, to);
			
			from = (new Double((maxX + 180.0) * 10.0).intValue());
			to = (new Double((180.0 + 180.0) * 10.0).intValue());
			writeToExistFiles(rstring, from, to);
			
		}
		else {
			int from = (new Double((minX + 180.0) * 10.0).intValue());
			int to = (new Double((maxX + 180.0) * 10.0).intValue());
			writeToExistFiles(rstring, from, to);
		}
	}

	private void writeToExistFiles(String rstring, int from, int to) {
		for(int i = from; i <= to; i++) {
			String filePrefix = String.format("%04d", i);
			if(files.contains(filePrefix)) {
				slicer.writeOut(rstring, filePrefix);
			}
		}
	}
	
	private Polygon movePolygonBack(Polygon translatedPolygon) {
		if(weAreInRussia) {
			return (Polygon) new GeometryEditor(fatory).edit(translatedPolygon, new GeometryEditor.CoordinateOperation() {
				
				@Override
				public Coordinate[] edit(Coordinate[] paramArrayOfCoordinate,
						Geometry paramGeometry) {
					
					Coordinate[] result = new Coordinate[paramArrayOfCoordinate.length];
					
					int i = 0;
					for(Coordinate c : paramArrayOfCoordinate) {
						result[i++] = new Coordinate(moveBack(c.x), c.y);
					}
					
					return result;
				}
			});
		}
		 return translatedPolygon;
	}

	private JSONObject mergeDeloneyCenter(JSONObject centerFeature, Polygon polygon, String resultType) {
		String id = centerFeature.getString("id");
		
		JSONObject meta = centerFeature.getJSONObject(GeoJsonWriter.META);
		meta.put("sitePoint", centerFeature.getJSONObject(GeoJsonWriter.GEOMETRY).get(GeoJsonWriter.COORDINATES));
		
		JSONObject rfeature = GeoJsonWriter.createFeature(id, resultType, new HashMap<String, String>(), polygon, meta);
		rfeature.put(GeoJsonWriter.PROPERTIES, centerFeature.getJSONObject(GeoJsonWriter.PROPERTIES));
		return rfeature;
	}


}
