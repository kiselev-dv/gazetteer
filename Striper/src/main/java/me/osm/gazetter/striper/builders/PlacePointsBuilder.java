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

import javax.xml.parsers.FactoryConfigurationError;

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
import com.vividsolutions.jts.geom.GeometryCollection;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
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
	
	private static final Map<Coordinate, JSONObject> cityes = new HashMap<>(); 
	private static final Map<Coordinate, JSONObject> neighbours = new HashMap<>(); 
	
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
			
			String id = GeoJsonWriter.getId(FeatureTypes.PLACE_DELONEY_FTYPE, pnt, meta);
			JSONObject feature = GeoJsonWriter.createFeature(id, FeatureTypes.PLACE_DELONEY_FTYPE, node.tags, pnt, meta);
			
			if(PLACE_CITY.contains(node.tags.get("place"))) {
				cityes.put(getCoordinateFromGJSON(feature), feature);
			}
			
			if(PLACE_NEIGHBOUR.contains(node.tags.get("place"))) {
				neighbours.put(getCoordinateFromGJSON(feature), feature);
			}
		}
	}
	
	@Override
	public void afterLastRun() {
		
		VoronoiDiagramBuilder cvb = new VoronoiDiagramBuilder();
		
		Quadtree neighboursQT = new Quadtree();
		for (Entry<Coordinate, JSONObject> entry : neighbours.entrySet()) {
			neighboursQT.insert(new Envelope(entry.getKey()), entry.getValue());
		}
		
		cvb.setSites(cityes.keySet());
		cvb.setClipEnvelope(new Envelope(-180.0, 180.0, 80.0, -80.0));
		
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

	private void handleCityVoronoy(JSONObject placeFeature, Polygon cityPolygon, Quadtree neighboursQT) {
		
		JSONObject rfeature = mergeDeloneyCenter(placeFeature, cityPolygon, FeatureTypes.PLACE_DELONEY_FTYPE);
		String rstring = rfeature.toString();
		
		Envelope cityPolygonEnv = cityPolygon.getEnvelopeInternal();
		
		int from = (new Double((cityPolygonEnv.getMinX() + 180.0) * 10.0).intValue());
		int to = (new Double((cityPolygonEnv.getMaxX() + 180.0) * 10.0).intValue());
				
		for(int i = from; i <= to; i++) {
			slicer.writeOut(rstring, String.format("%04d", i));
		}
		
		buildNighboursVoronoiPolygons(cityPolygon, neighboursQT, rfeature,
				cityPolygonEnv);
	}

	private void buildNighboursVoronoiPolygons(Polygon cityPolygon,
			Quadtree neighboursQT, JSONObject cityFeature, Envelope cityPolygonEnv) {
		
		List<Coordinate> neighboursCoords = new ArrayList<>();
		
		@SuppressWarnings("unchecked")
		List<JSONObject> neighbourCandidates = neighboursQT.query(cityPolygonEnv);
		
		for(JSONObject neighbour : neighbourCandidates) {
			Coordinate coordinate = getCoordinateFromGJSON(neighbour);
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

	private JSONObject mergeDeloneyCenter(JSONObject p, Polygon cityPolygon, String resultType) {
		String id = p.getString("id");
		
		JSONObject meta = p.getJSONObject(GeoJsonWriter.META);
		meta.put("sitePoint", p.getJSONObject(GeoJsonWriter.GEOMETRY).get(GeoJsonWriter.COORDINATES));
		
		JSONObject rfeature = GeoJsonWriter.createFeature(id, resultType, new HashMap<String, String>(), cityPolygon, meta);
		rfeature.put(GeoJsonWriter.PROPERTIES, p.getJSONObject(GeoJsonWriter.PROPERTIES));
		return rfeature;
	}

	private void handleNeighbour(Polygon neighbourPolygon, JSONObject cityFeature,
			JSONObject neighbourFeature) {
		
		JSONObject rfeature = mergeDeloneyCenter(neighbourFeature, neighbourPolygon, FeatureTypes.NEIGHBOUR_DELONEY_FTYPE);
		rfeature.put("cityID", cityFeature.getString("id"));
		String rstring = rfeature.toString();
		
		Envelope env = neighbourPolygon.getEnvelopeInternal();
		
		int from = (new Double((env.getMinX() + 180.0) * 10.0).intValue());
		int to = (new Double((env.getMaxX() + 180.0) * 10.0).intValue());
				
		for(int i = from; i <= to; i++) {
			slicer.writeOut(rstring, String.format("%04d", i));
		}
	}

}
