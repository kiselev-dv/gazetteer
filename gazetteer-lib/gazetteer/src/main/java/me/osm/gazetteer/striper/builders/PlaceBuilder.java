package me.osm.gazetteer.striper.builders;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.collections4.map.LRUMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
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
import com.vividsolutions.jts.triangulate.quadedge.Vertex;

import me.osm.gazetteer.addresses.AddressesUtils;
import me.osm.gazetteer.addresses.NamesMatcher;
import me.osm.gazetteer.addresses.impl.NamesMatcherImpl;
import me.osm.gazetteer.striper.BoundariesFallbacker;
import me.osm.gazetteer.striper.FeatureTypes;
import me.osm.gazetteer.striper.GeoJsonWriter;
import me.osm.gazetteer.striper.JSONFeature;
import me.osm.gazetteer.striper.Slicer;
import me.osm.gazetteer.striper.builders.handlers.BoundariesHandler;
import me.osm.gazetteer.striper.builders.handlers.PlacePointHandler;
import me.osm.gazetteer.striper.readers.PointsReader.Node;
import me.osm.gazetteer.striper.readers.RelationsReader.Relation;
import me.osm.gazetteer.striper.readers.WaysReader.Way;
import me.osm.gazetteer.utils.FileLinesOffsetsIndex;
import me.osm.gazetteer.utils.index.IndexFactory;

public class PlaceBuilder extends BoundariesBuilder {

	private final Map<String, JSONObject> cachedCityNodes = new LRUMap<>(1000);
	private volatile FileLinesOffsetsIndex cityNodesFileIndex;
	private final double citiesIndexPreloadFactor = 0.5;

	private static final String CITY = "city";
	private static final String NEIGHBOUR = "neighbour";

	private NamesMatcher namesMatcher = new NamesMatcherImpl();

	private static final Logger log = LoggerFactory
			.getLogger(PlaceBuilder.class.getName());

	private static GeometryFactory fatory = new GeometryFactory();
	private PlacePointHandler handler;

	private long cityPointsCounter = 0;

	private Map<Coordinate, String> cityes = new HashMap<>();
	private Map<Coordinate, String> neighbours = new HashMap<>();
	private Quadtree cityesIndex = new Quadtree();

	private final BBOX originalBBOX = new BBOX();
	private final BBOX translatedBBOX = new BBOX();

	// this tricky flag will mess everything around 180*
	private volatile boolean weAreInRussia = false;

	private final Set<String> files = new HashSet<>();

	private File cityNodesIndex;
	private PrintWriter fileWriter;

	private static final Set<String> PLACE_CITY = new HashSet<String>(
			Arrays.asList(new String[] { "city", "town", "hamlet", "village",
					"isolated_dwelling" }));

	private static final Set<String> PLACE_NEIGHBOUR = new HashSet<String>(
			Arrays.asList(new String[] { "suburb", "neighbourhood", "quarter" }));

	private static final double DEGREE_OFFSET = -90.0;

	private int cityHit = 0;
	private int cityMiss = 0;
	private boolean buildCitiesIndex;
	private boolean mergeCityPointsToBoundary;
	private boolean doNearestCityLookup;

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

	public PlaceBuilder(PlacePointHandler slicer,
			BoundariesHandler handler,
			BoundariesFallbacker fallback,
			IndexFactory indexFactory,
			File cityNodesIndex,
			boolean mergeCityPointsToBoundary,
			boolean doNearestCityLookup) {

		super(handler, indexFactory, fallback);
		this.handler = slicer;

		this.buildCitiesIndex = mergeCityPointsToBoundary || doNearestCityLookup;
		this.mergeCityPointsToBoundary = mergeCityPointsToBoundary;
		this.doNearestCityLookup = doNearestCityLookup;

		if (buildCitiesIndex) {
			this.cityNodesIndex = cityNodesIndex;
			try {
				fileWriter = new PrintWriter(new FileOutputStream(this.cityNodesIndex));
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

	@Override
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

			if (buildCitiesIndex) {
				String id = GeoJsonWriter.getId(FeatureTypes.PLACE_DELONEY_FTYPE,
						pnt, meta);
				JSONObject feature = GeoJsonWriter.createFeature(id,
						FeatureTypes.PLACE_DELONEY_FTYPE, node.tags, pnt, meta);

				Envelope envelope = pnt.getEnvelopeInternal();
				if (PLACE_CITY.contains(node.tags.get("place"))) {
					cityesIndex.insert(envelope, String.valueOf(node.id));
					saveCityToIndex(node.id, coordinate, CITY, feature);
					files.add(Slicer.getFilePrefix(node.lon, node.lat));
				}

				if (PLACE_NEIGHBOUR.contains(node.tags.get("place"))) {
					saveCityToIndex(node.id, coordinate, NEIGHBOUR, feature);
					files.add(Slicer.getFilePrefix(node.lon, node.lat));
				}
			}
		}

		if (node.tags.containsKey("addr:housenumber")) {
			originalBBOX.extend(node.lon, node.lat);
			translatedBBOX.extend(moveTo(node.lon), node.lat);
		}
	}

	@Override
	public void firstRunDoneNodes() {
		// Boundaries are loaded during ways second run
		// and relations second run
		if (mergeCityPointsToBoundary) {

			log.info("Saved {} city/neighourhoods for delaney", cityPointsCounter);

			fileWriter.flush();
			fileWriter.close();

			try {
				loadCityNodesIndex();
			} catch (IOException e) {
				throw new RuntimeException("Failed to sort city nodes index", e);
			}
		}
	}

	private void saveCityToIndex(long nodeId, Coordinate coordinate, String type, JSONObject feature) {
		cityPointsCounter++;

		StringBuilder builder = new StringBuilder();

		builder.append(nodeId).append("\t");
		builder.append(coordinate.x).append("\t");
		builder.append(coordinate.y).append("\t");
		builder.append(type).append("\t");
		builder.append(feature.toString());

		fileWriter.println(builder.toString());
	}

	@Override
	public void secondRunDoneRelations() {
		if(doNearestCityLookup) {
			buildVoronoyDiagrams();
		}

		//shutdown executor services
		super.secondRunDoneRelations();
		this.handler.freeThreadPool(getThreadPoolUser());
	}

	@Override
	public void close() {
		super.close();

		try {
			if (cityNodesFileIndex != null) {
				cityNodesFileIndex.close();
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void loadCityNodesIndex() throws IOException {
		assert buildCitiesIndex;

		long bufferSize = cityPointsCounter;
		if (cityPointsCounter > 3000) {
			bufferSize = (long) (bufferSize * citiesIndexPreloadFactor);
		}

		if (cityNodesFileIndex == null) {
			log.info("Load index for nearest city and neighbour");

			cityNodesFileIndex = new FileLinesOffsetsIndex(
					new RandomAccessFile(this.cityNodesIndex, "r"),
					new FileLinesOffsetsIndex.Accessor() {
						@Override
						public String getKey(String line) {
							String[] split = StringUtils.split(line, '\t');

							String id = split[0];
							double lon = Double.valueOf(split[1]);
							double lat = Double.valueOf(split[2]);

							String type = split[3];
							if(CITY.equals(type)) {
								cityes.put(new Coordinate(lon, lat), id);
							}

							if (NEIGHBOUR.equals(type)) {
								neighbours.put(new Coordinate(lon, lat), id);
							}

							return split[0];
						}
					},
					false,
					(int)bufferSize);
		}

		cityNodesFileIndex.build();

	}

	//single threaded
	private void buildVoronoyDiagrams() {

		try {
			loadCityNodesIndex();
		} catch (IOException e) {
			throw new RuntimeException("Failed to load cities index", e);
		}

		long started = new Date().getTime();

		// Possibly we processing Russia.
		// And we have wrong originalBBOX which covers whole planet.
		// So lets translate all coordinates for Vronoi diagramm and
		// move it back while writing
		if (originalBBOX.getDX() > translatedBBOX.getDX() + 0.0001) {
			weAreInRussia = true;
			log.trace("Wrap 180 degree line.");

			Map<Coordinate, String> russianCityes = new HashMap<>();
			for (Entry<Coordinate, String> entry : cityes.entrySet()) {
				Coordinate c = entry.getKey();
				c.x = moveTo(c.x);
				russianCityes.put(c, entry.getValue());
			}
			cityes = russianCityes;

			Map<Coordinate, String> russianNeighbours = new HashMap<>();
			for (Entry<Coordinate, String> entry : neighbours.entrySet()) {
				Coordinate c = entry.getKey();
				c.x = moveTo(c.x);
				russianNeighbours.put(c, entry.getValue());
			}
			neighbours = russianNeighbours;
		}

		VoronoiDiagramBuilder cvb = new VoronoiDiagramBuilder();

		Quadtree neighboursQT = new Quadtree();
		for (Entry<Coordinate, String> entry : neighbours.entrySet()) {
			neighboursQT.insert(new Envelope(entry.getKey()), entry.getValue());
		}

		cvb.setSites(cityes.keySet());

		BBOX bbox = weAreInRussia ? translatedBBOX : originalBBOX;
		cvb.setClipEnvelope(new Envelope(bbox.minX, bbox.maxX, bbox.minY,
				bbox.maxY));

		QuadEdgeSubdivision subdivision = cvb.getSubdivision();

		Map<String, Set<String>> nCities = new HashMap<String, Set<String>>();

		@SuppressWarnings("unchecked")
		List<Vertex[]> triangleVertices = (List<Vertex[]>)subdivision.getTriangleVertices(false);
		for (Vertex[] vs : triangleVertices) {
			putNeighbours(vs[0], vs[1], nCities);
			putNeighbours(vs[0], vs[2], nCities);
			putNeighbours(vs[1], vs[2], nCities);
		}

		try {
			@SuppressWarnings("unchecked")
			Collection<Polygon> cityVoronoiPolygons =
				subdivision.getVoronoiCellPolygons(fatory);

			for (Polygon cityPolygon : cityVoronoiPolygons) {
				String cityId = cityes.get(cityPolygon
						.getUserData());

				JSONObject cityJSON = getCityById(cityId);
				if (cityJSON != null) {
					Set<JSONObject> neighbourCities = new HashSet<>();
					for(String cityNodeId : nCities.get(cityId)) {
						JSONObject neighbourCity = getCityById(cityNodeId);
						if (neighbourCity != null) {
							neighbourCities.add(neighbourCity);
						}
					}

					handleCityVoronoy(cityJSON, cityPolygon, neighboursQT, neighbourCities);
				}
			}
		}
		catch (IllegalArgumentException e) {
			log.warn("Failed to build Voronoy cell");
		}

		log.info("Done build cities Voronoy cells in {}",
				DurationFormatUtils.formatDurationHMS(new Date().getTime() - started));

		log.info("Cyties cache hit/miss {}/{}", cityHit, cityMiss);

	}

	private synchronized JSONObject getCityById(final String cityId) {

		JSONObject cached = cachedCityNodes.get(cityId);
		if (cached != null) {
			cityHit ++;
			return cached;
		}
		cityMiss ++;

		try {

			String line = cityNodesFileIndex.get(cityId);

			if (line != null) {
				JSONObject obj = new JSONObject(StringUtils.split(line, '\t')[4]);
				cachedCityNodes.put(cityId, obj);
				return obj;
			}
			else {
				log.warn("Cities deloney for {} not found", cityId);
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

		return null;
	}

	private void putNeighbours(Vertex vertexA, Vertex vertexB,
			Map<String, Set<String>> nCities) {

		if(hasNaNCoordinates(vertexA) || hasNaNCoordinates(vertexB)) {
			log.warn("Skip neughbours, due to NaN coordinates.");
			return;
		}

		String ca = cityes.get(vertexA.getCoordinate());
		String cb = cityes.get(vertexB.getCoordinate());

		if(nCities.get(ca) == null){
			nCities.put(ca, new HashSet<String>());
		}

		if(nCities.get(cb) == null){
			nCities.put(cb, new HashSet<String>());
		}

		nCities.get(ca).add(cb);
		nCities.get(cb).add(ca);
	}

	private boolean hasNaNCoordinates(Vertex vertex) {
		return Double.isNaN(vertex.getX()) || Double.isNaN(vertex.getY());
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
	 * @param nCities
	 * */
	private void handleCityVoronoy(JSONObject placeFeature,
			Polygon cityPolygon, Quadtree neighboursQT, Set<JSONObject> nCities) {

		Polygon originalCityPolygon = weAreInRussia ? movePolygonBack(cityPolygon)
				: cityPolygon;

		// original coords
		JSONObject rfeature = mergeDeloneyCenter(placeFeature,
				originalCityPolygon, FeatureTypes.PLACE_DELONEY_FTYPE);

		rfeature.put("neighbourCities", nCities);

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
		List<String> neighbourCandidates = neighboursQT
				.query(cityPolygonEnv);

		for (String neighbourId : neighbourCandidates) {

			JSONObject neighbour = getCityById(neighbourId);
			if (neighbour != null) {
				// original coordinates
				Coordinate coordinate = getCoordinateFromGJSON(neighbour);

				if (weAreInRussia) {
					coordinate.x = moveTo(coordinate.x);
				}

				if (cityPolygon.contains(fatory.createPoint(coordinate))) {
					neighboursCoords.add(coordinate);
				}
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
					String neighbourId = neighbours.get(neighbourPolygon
							.getUserData());

					JSONObject neighbourCityJSON = getCityById(neighbourId);
					if(neighbourCityJSON != null) {
						handleNeighbour(intersection, cityFeature, neighbourCityJSON);
					}
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
			String filePrefix = Slicer.formatFilePrefix(i, 1);
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

		if (mergeCityPointsToBoundary) {
			mergeWithCenter(featureWithoutGeometry, geometry);
		}

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

		if (mergeCityPointsToBoundary) {
			mergeWithCenter(featureWithoutGeometry, multiPolygon);
		}

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
					for(String ppId : (List<String>)cityesIndex.query(polygon.getEnvelopeInternal())) {
						JSONObject pp = getCityById(ppId);
						if (pp != null) {
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
