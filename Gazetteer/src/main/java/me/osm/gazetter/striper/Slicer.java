package me.osm.gazetter.striper;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import me.osm.gazetter.dao.FileWriteDao;
import me.osm.gazetter.dao.WriteDao;
import me.osm.gazetter.striper.builders.AddrPointsBuilder;
import me.osm.gazetter.striper.builders.BoundariesBuilder;
import me.osm.gazetter.striper.builders.Builder;
import me.osm.gazetter.striper.builders.HighwaysBuilder;
import me.osm.gazetter.striper.builders.PlaceBuilder;
import me.osm.gazetter.striper.builders.PoisBuilder;
import me.osm.gazetter.striper.builders.handlers.AddrPointHandler;
import me.osm.gazetter.striper.builders.handlers.BoundariesHandler;
import me.osm.gazetter.striper.builders.handlers.HighwaysHandler;
import me.osm.gazetter.striper.builders.handlers.JunctionsHandler;
import me.osm.gazetter.striper.builders.handlers.PlacePointHandler;
import me.osm.gazetter.striper.builders.handlers.PoisHandler;
import me.osm.gazetter.striper.readers.RelationsReader.Relation.RelationMember;
import me.osm.gazetter.striper.readers.RelationsReader.Relation.RelationMember.ReferenceType;
import me.osm.gazetter.striper.readers.WaysReader.Way;
import me.osm.gazetter.utils.GeometryUtils;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.io.WKTWriter;

public class Slicer implements BoundariesHandler, 
	AddrPointHandler, PlacePointHandler, HighwaysHandler, JunctionsHandler, PoisHandler {
	
	private static final Logger log = LoggerFactory.getLogger(Slicer.class.getName());
	private static final Set<String> threadPoolUsers = new HashSet<String>();

	private static final GeometryFactory factory = new GeometryFactory();
	private static final ExecutorService executorService = Executors.newFixedThreadPool(4);
	
	private static double dx = 0.1;
	private static double x0 = 0;
	
	private static WriteDao writeDAO;

	private static Slicer instance;
	
	public static final List<String> sliceTypes = Arrays.asList(
			"all", "boundaries", "places", "highways", "addresses", "pois"
	);
	
	public static double snap(double x) {
		return Math.round((x - x0)/ dx) * dx + x0;
	}
	
	public Slicer(String dirPath) {
		writeDAO = new FileWriteDao(new File(dirPath));
	}
	
	public static void run(String osmSlicesPath, String poiCatalogPath, List<String> types, List<String> exclude) {
		long start = new Date().getTime(); 
		
		log.info("Slice {}", types);
		
		instance = new Slicer(osmSlicesPath);
		
		List<Builder> builders = new ArrayList<>();
		
		Set<String> typesSet = new HashSet<String>(types);
		
		if(typesSet.contains("all") || typesSet.contains("boundaries")) {
			builders.add(new BoundariesBuilder(instance));
		}

		if(typesSet.contains("all") || typesSet.contains("places")) {
			builders.add(new PlaceBuilder(instance, instance));
		}

		if(typesSet.contains("all") || typesSet.contains("highways")) {
			builders.add(new HighwaysBuilder(instance, instance));
		}

		if(typesSet.contains("all") || typesSet.contains("addresses")) {
			builders.add(new AddrPointsBuilder(instance));
		}
		
		if(typesSet.contains("all") || typesSet.contains("pois")) {
			builders.add(new PoisBuilder(instance, poiCatalogPath, exclude));
		}
		
		
		Builder[] buildersArray = builders.toArray(new Builder[builders.size()]);
		new Engine().filter(osmSlicesPath, buildersArray);
		
		writeDAO.close();
		instance = null;
		log.info("Slice done in {}", DurationFormatUtils.formatDurationHMS(new Date().getTime() - start));
	}

	private static class SliceTask implements Runnable {

		private MultiPolygon multiPolygon;
		private Slicer slicer;
		private JSONObject featureWG;
		
		public SliceTask(JSONObject featureWG,
				MultiPolygon multiPolygon, Slicer slicer) {
			this.featureWG = featureWG;
			this.multiPolygon = multiPolygon;
			this.slicer = slicer;
		}

		@Override
		public void run() {
			slicer.stripeBoundary(featureWG, multiPolygon);
		}
		
	}
	
	@Override
	public void handleBoundary(JSONObject featureWG,
			MultiPolygon multiPolygon) {
		executorService.execute(new SliceTask(featureWG, multiPolygon, this));
	}

	private void stripeBoundary(JSONObject featureWithoutGeometry,
			MultiPolygon multiPolygon) {
		if(multiPolygon != null) {
			
			JSONObject meta = featureWithoutGeometry.getJSONObject(GeoJsonWriter.META);
			
			Envelope envelope = multiPolygon.getEnvelopeInternal();
			JSONArray bbox = new JSONArray();
			bbox.put(envelope.getMinX());
			bbox.put(envelope.getMinY());
			bbox.put(envelope.getMaxX());
			bbox.put(envelope.getMaxY());
			
			meta.put(GeoJsonWriter.ORIGINAL_BBOX, bbox);
			
			if(isPlaceBoundary(featureWithoutGeometry)) {
				if(!multiPolygon.isEmpty()) {
					meta.put(GeoJsonWriter.FULL_GEOMETRY, 
							GeoJsonWriter.geometryToJSON(multiPolygon.getGeometryN(0)));
				}
			}
			
			List<Polygon> polygons = new ArrayList<>();
			
			for(int i = 0; i < multiPolygon.getNumGeometries(); i++) {
				Polygon p = (Polygon) (multiPolygon.getGeometryN(i));
				
				if(p.isValid()) {
					stripe(p, polygons);
				}
				else {
					log.warn("Couldn't slice {} {}.\nPolygon:\n{}", new Object[]{
						meta.getString("type"), 
						meta.getLong("id"),
						new WKTWriter().write(p)
					});
				}
			}
			
			
			for(Polygon p : polygons) {
				String n = getFilePrefix(p.getEnvelope().getCentroid().getX());
				featureWithoutGeometry.put(GeoJsonWriter.GEOMETRY, GeoJsonWriter.geometryToJSON(p));
				String geoJSONString = featureWithoutGeometry.toString();
				writeOut(geoJSONString, n);
			}
		}
	}

	private boolean isPlaceBoundary(JSONObject featureWithoutGeometry) {
		return featureWithoutGeometry
				.getJSONObject(GeoJsonWriter.PROPERTIES).has("place");
	}

	public void writeOut(String line, String n) {
		
		String fileName = "stripe" + n + ".gjson";
		try {
			writeDAO.write(line, fileName);
		} catch (IOException e) {
			throw new RuntimeException("Couldn't write out " + fileName, e);
		}
	}

	private static void stripe(Polygon p, List<Polygon> result) {
		Polygon bbox = (Polygon) p.getEnvelope();
		
		Point centroid = bbox.getCentroid();
		double snapX = round(snap(centroid.getX()), 4);
		
		double minX = p.getEnvelopeInternal().getMinX();
		double maxX = p.getEnvelopeInternal().getMaxX();
		if(snapX > minX && snapX < maxX) {
			List<Polygon> splitPolygon = GeometryUtils.splitPolygon(p, 
					factory.createLineString(new Coordinate[]{new Coordinate(snapX, 89.0), new Coordinate(snapX, -89.0)}));
			for(Polygon cp : splitPolygon) {
				stripe(cp, result);
			}
		}
		else {
			result.add(p);
		}

	}

	public static void stripe(LineString l, List<LineString> result) {
		Envelope bbox = l.getEnvelopeInternal();
		
		double snapX = round(snap(bbox.getMinX() + bbox.getWidth() / 2.0), 4);
		
		double minX = bbox.getMinX();
		double maxX = bbox.getMaxX();
		if(snapX > minX + 0.01 && snapX < maxX - 0.01) {
			
			LineString blade = factory.createLineString(new Coordinate[]{new Coordinate(snapX, bbox.getMinY()), new Coordinate(snapX, bbox.getMaxY())});
			Geometry intersection = l.intersection(blade);
			
			if(!intersection.isEmpty()) {
				LineString[] pair = null;
				for(int i = 0; i < intersection.getNumGeometries(); i++) {
					Geometry ip = intersection.getGeometryN(i).getCentroid();
					if(pair != null) {
						pair = GeometryUtils.split(pair[1], ip.getCoordinate(), false);
					}
					else {
						pair = GeometryUtils.split(l, ip.getCoordinate(), false);
					}
					stripe(pair[0], result);
				}
				stripe(pair[1], result);
			}
		}
		else {
			result.add(l);
		}
		
	}
	
	public static double round(double value, int places) {
	    if (places < 0) throw new IllegalArgumentException();

	    BigDecimal bd = new BigDecimal(value);
	    bd = bd.setScale(places, RoundingMode.HALF_UP);
	    return bd.doubleValue();
	}



	@Override
	public void handleAddrPoint(Map<String, String> attributes, Point point,
			JSONObject meta) {
		String id = GeoJsonWriter.getId(FeatureTypes.ADDR_POINT_FTYPE, point, meta);
		String n = getFilePrefix(point.getX());
		
		String geoJSONString = GeoJsonWriter.featureAsGeoJSON(id, FeatureTypes.ADDR_POINT_FTYPE, attributes, point, meta);
		
		assert GeoJsonWriter.getId(geoJSONString).equals(id) 
			: "Failed getId for " + geoJSONString;
		
		assert GeoJsonWriter.getFtype(geoJSONString).equals(FeatureTypes.ADDR_POINT_FTYPE) 
			: "Failed getFtype for " + geoJSONString;
		
		writeOut(geoJSONString, n);
	}

	public static String getFilePrefix(double x) {
		return String.format("%04d", (new Double((x + 180.0) * 10.0).intValue()));
	}

	@Override
	public void handlePlacePoint(Map<String, String> tags, Point pnt,
			JSONObject meta) {
		String fid = GeoJsonWriter.getId(FeatureTypes.PLACE_POINT_FTYPE, pnt, meta);
		String n = getFilePrefix(pnt.getX());
		String geoJSONString = GeoJsonWriter.featureAsGeoJSON(fid, FeatureTypes.PLACE_POINT_FTYPE, tags, pnt, meta);
		
		assert GeoJsonWriter.getId(geoJSONString).equals(fid) 
			: "Failed getId for " + geoJSONString;

		assert GeoJsonWriter.getFtype(geoJSONString).equals(FeatureTypes.PLACE_POINT_FTYPE) 
			: "Failed getFtype for " + geoJSONString;
		
		writeOut(geoJSONString, n);
	}

	@Override
	public void handleJunction(Coordinate coordinates, long nodeID,
			List<Long> highways) {
		
		Point pnt = factory.createPoint(coordinates);
		JSONObject meta = new JSONObject();
		meta.put("id", nodeID);
		meta.put("type", "node");
		String fid = GeoJsonWriter.getId(FeatureTypes.JUNCTION_FTYPE, pnt, meta);
		String n = getFilePrefix(pnt.getX());
		
		@SuppressWarnings("unchecked")
		JSONObject r = GeoJsonWriter.createFeature(fid, FeatureTypes.JUNCTION_FTYPE, Collections.EMPTY_MAP, pnt, meta);
		r.put("ways", new JSONArray(highways));
		GeoJsonWriter.addTimestamp(r);
		
		String geoJSONString = r.toString();
		
		assert GeoJsonWriter.getId(geoJSONString).equals(fid) 
			: "Failed getId for " + geoJSONString;

		assert GeoJsonWriter.getFtype(geoJSONString).equals(FeatureTypes.JUNCTION_FTYPE) 
			: "Failed getFtype for " + geoJSONString;
		
		writeOut(geoJSONString, n);
	}

	@Override
	public void handleHighway(LineString geometry, Way way) {
		
		JSONObject meta = new JSONObject();
		meta.put("id", way.id);
		meta.put("type", "way");
		
		Envelope env = geometry.getEnvelopeInternal();
		
		// most of highways hits only one stripe so it's faster to write
		// it into all of them without splitting 
		int min = new Double((env.getMinX() + 180.0) * 10.0).intValue();
		int max = new Double((env.getMaxX() + 180.0) * 10.0).intValue();
		
		Point centroid = geometry.getCentroid();
		
		String fid = GeoJsonWriter.getId(FeatureTypes.HIGHWAY_FEATURE_TYPE, centroid, meta);
		meta.put(GeoJsonWriter.FULL_GEOMETRY, GeoJsonWriter.geometryToJSON(geometry));
		String geoJSONString = GeoJsonWriter.featureAsGeoJSON(fid, FeatureTypes.HIGHWAY_FEATURE_TYPE, way.tags, geometry, meta);
		
		assert GeoJsonWriter.getId(geoJSONString).equals(fid) 
			: "Failed getId for " + geoJSONString;
	
		assert GeoJsonWriter.getFtype(geoJSONString).equals(FeatureTypes.HIGHWAY_FEATURE_TYPE) 
			: "Failed getFtype for " + geoJSONString;
		
		if(min == max) {
			String n = getFilePrefix(centroid.getX());
			writeOut(geoJSONString, n);
		}
		else if(max - min == 1) {
			//it's faster to write geometry as is in such case.
			for(int i = min; i <= max; i++) {
				String n = String.format("%04d", i); 
				writeOut(geoJSONString, n);
			}
		}
		else {
			List<LineString> segments = new ArrayList<>();
			try {
				stripe(geometry, segments);
			}
			catch (Throwable t) {
				log.error("Failed to stripe {}. {}", geometry, t.toString());
			}
			for(LineString stripe : segments) {
				String n = getFilePrefix(stripe.getCentroid().getX());
				String featureAsGeoJSON = GeoJsonWriter.featureAsGeoJSON(fid, FeatureTypes.HIGHWAY_FEATURE_TYPE, way.tags, stripe, meta);
				
				assert GeoJsonWriter.getId(featureAsGeoJSON).equals(fid) 
					: "Failed getId for " + featureAsGeoJSON;
		
				assert GeoJsonWriter.getFtype(featureAsGeoJSON).equals(FeatureTypes.HIGHWAY_FEATURE_TYPE) 
					: "Failed getFtype for " + featureAsGeoJSON;
				
				writeOut(featureAsGeoJSON, n);
			}
		}
	}

	@Override
	public void handlePoi(Set<String>types, Map<String, String> attributes, Point point,
			JSONObject meta) {
		
		String id = GeoJsonWriter.getId(FeatureTypes.POI_FTYPE, point, meta);
		String n = getFilePrefix(point.getX());
		
		JSONObject feature = GeoJsonWriter.createFeature(id, FeatureTypes.POI_FTYPE, attributes, point, meta);
		feature.put("poiTypes", new JSONArray(types));
		
		String geoJSONString = feature.toString();
		
		assert GeoJsonWriter.getId(geoJSONString).equals(id) 
			: "Failed getId for " + geoJSONString;
		
		assert GeoJsonWriter.getFtype(geoJSONString).equals(FeatureTypes.POI_FTYPE) 
			: "Failed getFtype for " + geoJSONString;
		
		writeOut(geoJSONString, n);
	}

	@Override
	public synchronized void freeThreadPool(String user) {
		threadPoolUsers.remove(user);
		if(threadPoolUsers.size() == 0) {
			executorService.shutdown();
			try {
				executorService.awaitTermination(10, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				log.error("Termination awaiting was interrupted", e);
			}
		}
	}

	public synchronized void newThreadpoolUser(String user) {
		threadPoolUsers.add(user);
	}

	@Override
	public void handleAddrPoint2Building(String n, long nodeId, long wayId,
			Map<String, String> wayTags) {
		
		writePnt2Building(FeatureTypes.ADDR_NODE_2_BUILDING, n, nodeId, wayId, wayTags);
	}


	@Override
	public void handlePoi2Building(String n, long nodeId, long lineId,
			Map<String, String> linetags) {
		writePnt2Building(FeatureTypes.POI_2_BUILDING, n, nodeId, lineId, linetags);
	}

	private void writePnt2Building(String ftype, String n, long nodeId, long wayId,
			Map<String, String> wayTags) {
		String id = ftype + "-" + wayId + "-" + nodeId;
		
		JSONFeature result = new JSONFeature();
		result.put("id", id);
		result.put("ftype", ftype);
		GeoJsonWriter.addTimestamp(result);
		
		JSONObject meta = new JSONObject();
		meta.put("id", wayId);
		meta.put("type", "way");
		
		result.put(GeoJsonWriter.META, meta);
		result.put("nodeId", nodeId);
		result.put(GeoJsonWriter.PROPERTIES, new JSONObject(wayTags));
		
		String geoJSONString = result.toString();
		
		assert GeoJsonWriter.getId(geoJSONString).equals(id) 
			: "Failed getId for " + geoJSONString;
		
		assert GeoJsonWriter.getFtype(geoJSONString).equals(ftype) 
			: "Failed getFtype for " + geoJSONString;
		
		writeOut(geoJSONString, n);
	}

	@Override
	public void handleAssociatedStreet(int minN, int maxN, List<Long> wayIds,
			List<RelationMember> buildings, long relationId,
			Map<String, String> relAttributes) {
		
		String id = FeatureTypes.ASSOCIATED_STREET + "-" + relationId;
		
		if(minN <= maxN) {
			for(int i = minN; i <= maxN; i++) {
				String n = String.format("%04d", i);
				
				JSONObject feature = new JSONFeature();

				JSONObject meta = new JSONObject();
				meta.put("id", relationId);
				meta.put("type", "relation");
				
				feature.put("id", id);
				feature.put("ftype", FeatureTypes.ASSOCIATED_STREET);
				feature.put("type", "Feature");
				feature.put(GeoJsonWriter.PROPERTIES, relAttributes);
				feature.put(GeoJsonWriter.META, meta);
				
				JSONArray buildingsArray = new JSONArray();
				for(RelationMember rm : buildings) {
					buildingsArray.put(firstCharOfType(rm.type) + rm.ref);
				}
				
				feature.put("buildings", buildingsArray);
				feature.put("associatedWays", new JSONArray(wayIds));
				
				GeoJsonWriter.addTimestamp(feature);
				
				writeOut(feature.toString(), n);
			}
		}
		
	}

	private String firstCharOfType(ReferenceType type) {
		switch (type) {
		case NODE:
			return "n";
		case WAY:
			return "w";
		case RELATION:
			return "r";
		}
		return null;
	}
}
