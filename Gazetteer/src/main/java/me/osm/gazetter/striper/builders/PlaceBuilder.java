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

import me.osm.gazetter.addresses.AddressesUtils;
import me.osm.gazetter.matchers.NamesMatcher;
import me.osm.gazetter.matchers.NamesMatcherImpl;
import me.osm.gazetter.striper.FeatureTypes;
import me.osm.gazetter.striper.GeoJsonWriter;
import me.osm.gazetter.striper.JSONFeature;
import me.osm.gazetter.striper.Slicer;
import me.osm.gazetter.striper.readers.PointsReader.Node;
import me.osm.gazetter.striper.readers.RelationsReader.Relation;
import me.osm.gazetter.striper.readers.WaysReader.Way;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.geom.util.GeometryEditor;
import com.vividsolutions.jts.index.quadtree.Quadtree;
import com.vividsolutions.jts.triangulate.VoronoiDiagramBuilder;
import com.vividsolutions.jts.triangulate.quadedge.QuadEdgeSubdivision;

public class PlaceBuilder extends BoundariesBuilder {

	private NamesMatcher namesMatcher = new NamesMatcherImpl();
	
	private static final Logger log = LoggerFactory
			.getLogger(PlaceBuilder.class.getName());

	public static interface PlacePointHandler extends FeatureHandler {
		public void handlePlacePoint(Map<String, String> tags, Point pnt,
				JSONObject meta);
		
		public void writeOut(String line, String n);
	}

	private static GeometryFactory fatory = new GeometryFactory();
	private PlacePointHandler handler;

	private Map<Coordinate, JSONObject> cityes = new HashMap<>();
	private Map<Coordinate, JSONObject> neighbours = new HashMap<>();
	private Quadtree cityesIndex = new Quadtree();

	private final BBOX originalBBOX = new BBOX();
	private final BBOX translatedBBOX = new BBOX();

	// this tricky flag will mess everything around 180*
	private volatile boolean weAreInRussia = false;

	private final Set<String> files = new HashSet<>();

	private static final Set<String> PLACE_CITY = new HashSet<String>(
			Arrays.asList(new String[] { "city", "town", "hamlet", "village",
					"isolated_dwelling" }));

	private static final Set<String> PLACE_NEIGHBOUR = new HashSet<String>(
			Arrays.asList(new String[] { "suburb", "neighbourhood", "quarter" }));

	private static final double DEGREE_OFFSET = -90.0;

	public static final double moveTo(double x) {
		if (x < DEGREE_OFFSET) {
			return x + 360 + DEGREE_OFFSET;
		}
		return x + DEGREE_OFFSET;
	}

	public static final double moveBack(double x) {
		if (x > -DEGREE_OFFSET) {
			return x - 360 - DEGREE_OFFSET;
		}
		return x - DEGREE_OFFSET;
	}

	public PlaceBuilder(PlacePointHandler slicer, BoundariesHandler handler) {
		super(handler);
		this.handler = slicer;
	}
	
	protected boolean filterByTags(Map<String, String> tags) {
		return tags.containsKey("place") 
				&& (PLACE_CITY.contains(tags.get("place")) || PLACE_NEIGHBOUR.contains(tags.get("place"))); 
	}

	@Override
	public void handle(Node node) {
		super.handle(node);
		
		if (filterByTags(node.tags)) {
			Coordinate coordinate = new Coordinate(node.lon, node.lat);
			Point pnt = fatory.createPoint(coordinate);

			JSONObject meta = new JSONObject();
			meta.put("type", "node");
			meta.put("id", node.id);

			handler.handlePlacePoint(node.tags, pnt, meta);

			originalBBOX.extend(node.lon, node.lat);
			translatedBBOX.extend(moveTo(node.lon), node.lat);

			String id = GeoJsonWriter.getId(FeatureTypes.PLACE_DELONEY_FTYPE,
					pnt, meta);
			JSONObject feature = GeoJsonWriter.createFeature(id,
					FeatureTypes.PLACE_DELONEY_FTYPE, node.tags, pnt, meta);

			Envelope envelope = pnt.getEnvelopeInternal();
			if (PLACE_CITY.contains(node.tags.get("place"))) {
				cityesIndex.insert(envelope, feature);
				cityes.put(coordinate, feature);
				files.add(Slicer.getFilePrefix(node.lon));
			}

			if (PLACE_NEIGHBOUR.contains(node.tags.get("place"))) {
				neighbours.put(coordinate, feature);
				files.add(Slicer.getFilePrefix(node.lon));
			}
		}

		if (node.tags.containsKey("addr:housenumber")) {
			originalBBOX.extend(node.lon, node.lat);
			translatedBBOX.extend(moveTo(node.lon), node.lat);
		}
	}

	@Override
	public void secondRunDoneRelations() {
		buildVoronoyDiagrams();
		
		//shutdown executor services
		super.secondRunDoneRelations();
		this.handler.freeThreadPool(getThreadPoolUser());
	}

	//single threaded
	private void buildVoronoyDiagrams() {
		// Possibly we processing Russia.
		// And we have wrong originalBBOX which covers whole planet.
		// So lets translate all coordinates for Vronoi diagramm and
		// move it back while writing
		if (originalBBOX.getDX() > translatedBBOX.getDX() + 0.0001) {
			weAreInRussia = true;
			log.trace("Wrap 180 degree line.");

			Map<Coordinate, JSONObject> russianCityes = new HashMap<>();
			for (Entry<Coordinate, JSONObject> entry : cityes.entrySet()) {
				Coordinate c = entry.getKey();
				c.x = moveTo(c.x);
				russianCityes.put(c, entry.getValue());
			}
			cityes = russianCityes;

			Map<Coordinate, JSONObject> russianNeighbours = new HashMap<>();
			for (Entry<Coordinate, JSONObject> entry : neighbours.entrySet()) {
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
		cvb.setClipEnvelope(new Envelope(bbox.minX, bbox.maxX, bbox.minY,
				bbox.maxY));

		QuadEdgeSubdivision subdivision = cvb.getSubdivision();

		@SuppressWarnings("unchecked")
		Collection<Polygon> cityVoronoiPolygons = subdivision
				.getVoronoiCellPolygons(fatory);

		for (Polygon cityPolygon : cityVoronoiPolygons) {
			JSONObject cityJSON = cityes.get((Coordinate) cityPolygon
					.getUserData());
			handleCityVoronoy(cityJSON, cityPolygon, neighboursQT);
		}
	}

	private static Coordinate getCoordinateFromGJSON(JSONObject gjson) {
		Object pc = gjson.getJSONObject(GeoJsonWriter.GEOMETRY).get(
				GeoJsonWriter.COORDINATES);

		if (pc instanceof JSONArray) {
			return new Coordinate(((JSONArray) pc).getDouble(0),
					((JSONArray) pc).getDouble(1));
		} else if (pc instanceof JSONString) {
			String[] split = StringUtils.split(
					((JSONString) pc).toJSONString(), ",[]");
			return new Coordinate(Double.parseDouble(split[0]),
					Double.parseDouble(split[1]));
		}

		return null;
	}

	/**
	 * placeFeature - original coordinates cityPolygon - translated coordinates
	 * neighboursQT - translated coordinates
	 * */
	private void handleCityVoronoy(JSONObject placeFeature,
			Polygon cityPolygon, Quadtree neighboursQT) {

		Polygon originalCityPolygon = weAreInRussia ? movePolygonBack(cityPolygon)
				: cityPolygon;

		// original coords
		JSONObject rfeature = mergeDeloneyCenter(placeFeature,
				originalCityPolygon, FeatureTypes.PLACE_DELONEY_FTYPE);
		
		assert GeoJsonWriter.getId(rfeature.toString()).equals(rfeature.optString("id")) 
			: "Failed getId for " + rfeature.toString();

		assert GeoJsonWriter.getFtype(rfeature.toString()).equals(FeatureTypes.PLACE_DELONEY_FTYPE) 
			: "Failed getFtype for " + rfeature.toString();

		// original coordinates
		writePolygonToExistFiles(originalCityPolygon, rfeature);

		buildNighboursVoronoiPolygons(cityPolygon, neighboursQT, rfeature);
	}

	/**
	 * cityPolygon - translated coordinates neighboursQT - translated
	 * coordinates cityFeature - originalCoords
	 * */
	private void buildNighboursVoronoiPolygons(Polygon cityPolygon,
			Quadtree neighboursQT, JSONObject cityFeature) {

		// translated coords
		List<Coordinate> neighboursCoords = new ArrayList<>();

		// translated coords
		Envelope cityPolygonEnv = cityPolygon.getEnvelopeInternal();

		@SuppressWarnings("unchecked")
		List<JSONObject> neighbourCandidates = neighboursQT
				.query(cityPolygonEnv);

		for (JSONObject neighbour : neighbourCandidates) {

			// original coordinates
			Coordinate coordinate = getCoordinateFromGJSON(neighbour);

			if (weAreInRussia) {
				coordinate.x = moveTo(coordinate.x);
			}

			if (cityPolygon.contains(fatory.createPoint(coordinate))) {
				neighboursCoords.add(coordinate);
			}
		}

		if (!neighboursCoords.isEmpty()) {
			VoronoiDiagramBuilder nvb = new VoronoiDiagramBuilder();

			nvb.setSites(neighboursCoords);
			nvb.setClipEnvelope(cityPolygonEnv);

			@SuppressWarnings({ "unchecked" })
			List<Polygon> neighboursPolygons = nvb.getSubdivision()
					.getVoronoiCellPolygons(fatory);

			for (Polygon neighbourPolygon : neighboursPolygons) {

				Polygon intersection = (Polygon) neighbourPolygon
						.intersection(cityPolygon);
				if (!intersection.isEmpty()) {
					JSONObject neighbour = neighbours.get(neighbourPolygon
							.getUserData());
					handleNeighbour(intersection, cityFeature, neighbour);
				}
			}
		}
	}

	/**
	 * neighbourPolygon - translated coordinates cityFeature - original
	 * coordinates neighbourFeature - original coordinates
	 * */
	private void handleNeighbour(Polygon neighbourPolygon,
			JSONObject cityFeature, JSONObject neighbourFeature) {

		if (weAreInRussia) {
			neighbourPolygon = movePolygonBack(neighbourPolygon);
		}

		JSONObject rfeature = mergeDeloneyCenter(neighbourFeature,
				neighbourPolygon, FeatureTypes.NEIGHBOUR_DELONEY_FTYPE);
		rfeature.put("cityID", cityFeature.getString("id"));
		
		assert GeoJsonWriter.getId(rfeature.toString()).equals(rfeature.optString("id")) 
			: "Failed getId for " + rfeature.toString();

		assert GeoJsonWriter.getFtype(rfeature.toString()).equals(FeatureTypes.NEIGHBOUR_DELONEY_FTYPE) 
			: "Failed getFtype for " + rfeature.toString();

		writePolygonToExistFiles(neighbourPolygon, rfeature);

	}

	private void writePolygonToExistFiles(Polygon polygon, JSONObject rfeature) {

		Envelope env = polygon.getEnvelopeInternal();

		// TODO: handle 180*
		double minX = env.getMinX();
		double maxX = env.getMaxX();

		double dx = Math.abs(maxX - minX);
		double dxt = Math.abs(moveTo(maxX) - moveTo(minX));

		Envelope envelope = polygon.getEnvelopeInternal();
		JSONArray bbox = new JSONArray();
		bbox.put(envelope.getMinX());
		bbox.put(envelope.getMinY());
		bbox.put(envelope.getMaxX());
		bbox.put(envelope.getMaxY());

		rfeature.getJSONObject(GeoJsonWriter.META).put(
				GeoJsonWriter.ORIGINAL_BBOX, bbox);
		GeoJsonWriter.addTimestamp(rfeature);

		String rstring = rfeature.toString();

		if (dxt < dx) {

			int from = (new Double((-180.0 + 180.0) * 10.0).intValue());
			int to = (new Double((minX + 180.0) * 10.0).intValue());
			writeToExistFiles(rstring, from, to);

			from = (new Double((maxX + 180.0) * 10.0).intValue());
			to = (new Double((180.0 + 180.0) * 10.0).intValue());
			writeToExistFiles(rstring, from, to);

		} else {
			int from = (new Double((minX + 180.0) * 10.0).intValue());
			int to = (new Double((maxX + 180.0) * 10.0).intValue());
			writeToExistFiles(rstring, from, to);
		}
	}

	private void writeToExistFiles(String rstring, int from, int to) {
		for (int i = from; i <= to; i++) {
			String filePrefix = String.format("%04d", i);
			if (files.contains(filePrefix)) {
				handler.writeOut(rstring, filePrefix);
			}
		}
	}

	private Polygon movePolygonBack(Polygon translatedPolygon) {
		if (weAreInRussia) {
			return (Polygon) new GeometryEditor(fatory).edit(translatedPolygon,
					new GeometryEditor.CoordinateOperation() {

						@Override
						public Coordinate[] edit(
								Coordinate[] paramArrayOfCoordinate,
								Geometry paramGeometry) {

							Coordinate[] result = new Coordinate[paramArrayOfCoordinate.length];

							int i = 0;
							for (Coordinate c : paramArrayOfCoordinate) {
								result[i++] = new Coordinate(moveBack(c.x), c.y);
							}

							return result;
						}
					});
		}
		return translatedPolygon;
	}

	private JSONObject mergeDeloneyCenter(JSONObject centerFeature,
			Polygon polygon, String resultType) {
		String id = centerFeature.getString("id");

		JSONObject meta = centerFeature.getJSONObject(GeoJsonWriter.META);
		meta.put(
				"sitePoint",
				centerFeature.getJSONObject(GeoJsonWriter.GEOMETRY).get(
						GeoJsonWriter.COORDINATES));

		JSONObject rfeature = GeoJsonWriter.createFeature(id, resultType,
				new HashMap<String, String>(), polygon, meta);
		rfeature.put(GeoJsonWriter.PROPERTIES,
				centerFeature.getJSONObject(GeoJsonWriter.PROPERTIES));
		return rfeature;
	}
	
	@Override
	protected void doneRelation(Relation rel, MultiPolygon geometry,
			JSONObject meta) {
		
		String fType = FeatureTypes.PLACE_BOUNDARY_FTYPE; 
		Point originalCentroid = geometry.getEnvelope().getCentroid();
		String id = GeoJsonWriter.getId(fType, originalCentroid, meta);
		JSONObject featureWithoutGeometry = GeoJsonWriter.createFeature(id, fType, rel.tags, null, meta);
		
		mergeWithCenter(featureWithoutGeometry, geometry);
		
		assert GeoJsonWriter.getId(featureWithoutGeometry.toString()).equals(id) 
			: "Failed getId for " + featureWithoutGeometry.toString();

		assert GeoJsonWriter.getFtype(featureWithoutGeometry.toString()).equals(FeatureTypes.PLACE_BOUNDARY_FTYPE) 
			: "Failed getFtype " + featureWithoutGeometry.toString();
		
		super.handler.handleBoundary(featureWithoutGeometry, geometry);
	}


	@Override
	protected void doneWay(Way line, MultiPolygon multiPolygon) {
		
		String fType = FeatureTypes.PLACE_BOUNDARY_FTYPE; 
		Point originalCentroid = multiPolygon.getEnvelope().getCentroid();
		JSONObject meta = getWayMeta(line);
		String id = GeoJsonWriter.getId(fType, originalCentroid, meta);
		JSONObject featureWithoutGeometry = GeoJsonWriter.createFeature(id, fType, line.tags, null, meta);
		
		mergeWithCenter(featureWithoutGeometry, multiPolygon);
		
		assert GeoJsonWriter.getId(featureWithoutGeometry.toString()).equals(id) 
			: "Failed getId for " + featureWithoutGeometry.toString();

		assert GeoJsonWriter.getFtype(featureWithoutGeometry.toString()).equals(FeatureTypes.PLACE_BOUNDARY_FTYPE) 
			: "Failed getFtype for " + featureWithoutGeometry.toString();
		
		super.handler.handleBoundary(featureWithoutGeometry, multiPolygon);
	}
	
	@Override
	public void firstRunDoneRelations() {
		super.firstRunDoneRelations();
		this.handler.newThreadpoolUser(getThreadPoolUser());
	}

	@SuppressWarnings("unchecked")
	private void mergeWithCenter(JSONObject featureWithoutGeometry,
			MultiPolygon geometry) {
		
		//we write places and neighbours, but merge only cities
		//so we need extra check
		if(PLACE_CITY.contains(featureWithoutGeometry.getJSONObject(GeoJsonWriter.PROPERTIES).optString("place"))) {
			
			HashSet<String> pbNamesSet = new HashSet<>(
					AddressesUtils.filterNameTags(
							featureWithoutGeometry.getJSONObject(GeoJsonWriter.PROPERTIES)).values());
			
			for(int i = 0; i < geometry.getNumGeometries(); i++) {
				Polygon polygon = (Polygon) geometry.getGeometryN(i);
				if(!polygon.isEmpty() && polygon.isValid()) {
					for(JSONObject pp : (List<JSONObject>)cityesIndex.query(polygon.getEnvelopeInternal())) {
						Coordinate c = getCoordinateFromGJSON(pp);
						if(polygon.contains(fatory.createPoint(c))) {

							String placeName = pp.getJSONObject(GeoJsonWriter.PROPERTIES).optString("name");
							if(namesMatcher.isPlaceNameMatch(placeName, pbNamesSet)) {
								handlePlaceMatch(featureWithoutGeometry, pp);
								return;
							}
							
						}
					}
				}
			}
		}
		
		
	}

	private void handlePlaceMatch(JSONObject boundary,
			JSONObject point) {
		
		//Keep all tagsa and coordinates from point for  boundary
		JSONObject placePointRefer = JSONFeature.asRefer(point);
		placePointRefer.put("id", placePointRefer.getString("id")
				.replace(FeatureTypes.PLACE_DELONEY_FTYPE, FeatureTypes.PLACE_POINT_FTYPE));
		placePointRefer.put(GeoJsonWriter.GEOMETRY, point.getJSONObject(GeoJsonWriter.GEOMETRY));
		boundary.put("placePoint", placePointRefer);
		
		//I'm not sure, that it's usefull to keep boundary tags
		//for point, because later we will search for boundaries
		//duiring the point in polygon join phase.
		//But still there is not so many polygonal
		//place boundaries with corresponding place points.
		point.put("boundary", JSONFeature.asRefer(boundary));
	}
}
