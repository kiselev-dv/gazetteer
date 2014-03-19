package me.osm.gazetter.striper;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import me.osm.gazetter.dao.FileWriteDao;
import me.osm.gazetter.dao.WriteDao;
import me.osm.gazetter.striper.builders.AddrPointsBuilder;
import me.osm.gazetter.striper.builders.AddrPointsBuilder.AddrPointHandler;
import me.osm.gazetter.striper.builders.BoundariesBuilder;
import me.osm.gazetter.striper.builders.BoundariesHandler;
import me.osm.gazetter.striper.builders.HighwaysBuilder;
import me.osm.gazetter.striper.builders.HighwaysBuilder.HighwaysHandler;
import me.osm.gazetter.striper.builders.HighwaysBuilder.JunctionsHandler;
import me.osm.gazetter.striper.builders.PlaceBuilder;
import me.osm.gazetter.striper.builders.PlaceBuilder.PlacePointHandler;
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
	AddrPointHandler, PlacePointHandler, HighwaysHandler, JunctionsHandler {
	
	private static final Logger log = LoggerFactory.getLogger(Slicer.class.getName());
	private static volatile int threadPoolUsers = 0;

	private static final GeometryFactory factory = new GeometryFactory();
	private static final ExecutorService executorService = Executors.newFixedThreadPool(4);
	
	private static double dx = 0.1;
	private static double x0 = 0;
	
	private static WriteDao writeDAO;

	private static Slicer instance;
	
	public static double snap(double x) {
		return Math.round((x - x0)/ dx) * dx + x0;
	}
	
	public Slicer(String dirPath) {
		writeDAO = new FileWriteDao(new File(dirPath));
	}
	
	public static void main(String[] args) {
		String osmFilePath = args[0];
		String osmSlicesPath = args[1];
		
		run(osmFilePath, osmSlicesPath);
	}

	public static void run(String osmFilePath, String osmSlicesPath) {
		long start = new Date().getTime(); 
		instance = new Slicer(osmSlicesPath);
		
		new Engine().filter(osmFilePath, 
				new BoundariesBuilder(instance), 
				new AddrPointsBuilder(instance), 
				new PlaceBuilder(instance, instance),
				new HighwaysBuilder(instance, instance));
		
		writeDAO.close();
		
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

	public void writeOut(String line, String n) {
		
		String fileName = "stripe" + n + ".gjson";
		try {
			writeDAO.write(line, fileName);
		} catch (IOException e) {
			log.error("Couldn't write out {}", fileName, e);
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

	private static void stripe(LineString l, List<LineString> result) {
		Envelope bbox = l.getEnvelopeInternal();
		
		double snapX = round(snap(bbox.getMinX() + bbox.getWidth() / 2.0), 4);
		
		double minX = bbox.getMinX();
		double maxX = bbox.getMaxX();
		if(snapX > minX && snapX < maxX) {
			LineString blade = factory.createLineString(new Coordinate[]{new Coordinate(snapX, 89.0), new Coordinate(snapX, -89.0)});
			Geometry intersection = l.intersection(blade);
			if(!intersection.isEmpty()) {
				//intersection in one point
				if(intersection.getNumGeometries() == 1) {
					Geometry ip = intersection.getCentroid();
					for(LineString split : GeometryUtils.split(l, ip.getCoordinate(), false)) {
						stripe(split, result);
					}
				}
				//curved line with 2 and more intersections
				else {
					for(int i = 0; i < intersection.getNumGeometries(); i++) {
						Geometry ip = intersection.getGeometryN(i).getCentroid();
						for(LineString split : GeometryUtils.split(l, ip.getCoordinate(), false)) {
							stripe(split, result);
						}
					}
				}
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
	public synchronized void beforeLastRun() {
		threadPoolUsers++;
	}

	@Override
	public synchronized void afterLastRun() {
		if(--threadPoolUsers == 0) {
			executorService.shutdown();
			try {
				executorService.awaitTermination(10, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				log.error("Termination awaiting was interrupted", e);
			}
		}
		
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
}
