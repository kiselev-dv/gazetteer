package me.osm.gazetter.striper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
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

import me.osm.gazetter.striper.builders.AddrPointsBuilder;
import me.osm.gazetter.striper.builders.AddrPointsBuilder.AddrPointHandler;
import me.osm.gazetter.striper.builders.BoundariesBuilder;
import me.osm.gazetter.striper.builders.HighwaysBuilder;
import me.osm.gazetter.striper.builders.HighwaysBuilder.HighwaysHandler;
import me.osm.gazetter.striper.builders.HighwaysBuilder.JunctionsHandler;
import me.osm.gazetter.striper.builders.PlacePointsBuilder;
import me.osm.gazetter.striper.builders.PlacePointsBuilder.PlacePointHandler;
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

public class Slicer implements BoundariesBuilder.BoundariesHandler, 
	AddrPointHandler, PlacePointHandler, HighwaysHandler, JunctionsHandler {
	
	private static final Logger log = LoggerFactory.getLogger(Slicer.class.getName()); 

	private static final GeometryFactory factory = new GeometryFactory();
	private static final ExecutorService executorService = Executors.newFixedThreadPool(4);
	
	private static double dx = 0.1;
	private static double x0 = 0;
	
	private String dirPath; 

	private static Slicer instance;
	
	public static double snap(double x) {
		return Math.round((x - x0)/ dx) * dx + x0;
	}
	
	public Slicer(String dirPath) {
		this.dirPath = dirPath;
		new File(this.dirPath).mkdirs();
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
				new PlacePointsBuilder(instance),
				new HighwaysBuilder(instance, instance));
		
		log.info("Slice done in {}", DurationFormatUtils.formatDurationHMS(new Date().getTime() - start));
	}

	private static class SliceTask implements Runnable {

		private Map<String, String> attributes;
		private MultiPolygon multiPolygon;
		private JSONObject meta;
		private Slicer slicer;
		
		public SliceTask(Map<String, String> attributes,
				MultiPolygon multiPolygon, JSONObject meta, Slicer slicer) {
			this.attributes = attributes;
			this.multiPolygon = multiPolygon;
			this.meta = meta;
			this.slicer = slicer;
		}

		@Override
		public void run() {
			slicer.stripeBoundary(attributes, multiPolygon, meta);
		}
		
	}
	
	@Override
	public void handleBoundary(Map<String, String> attributes,
			MultiPolygon multiPolygon, JSONObject meta) {
		executorService.execute(new SliceTask(attributes, multiPolygon, meta, this));
	}

	private void stripeBoundary(Map<String, String> attributes,
			MultiPolygon multiPolygon, JSONObject meta) {
		if(multiPolygon != null) {
			
			String id = null;
			
			Envelope envelope = multiPolygon.getEnvelopeInternal();
			JSONArray bbox = new JSONArray();
			bbox.put(envelope.getMinX());
			bbox.put(envelope.getMinY());
			bbox.put(envelope.getMaxX());
			bbox.put(envelope.getMaxY());
			
			if(attributes.containsKey("place")){
				id = GeoJsonWriter.getId(FeatureTypes.PLACE_BOUNDARY_FTYPE, multiPolygon.getEnvelope().getCentroid(), meta);
			}
			else {
				id = GeoJsonWriter.getId(FeatureTypes.ADMIN_BOUNDARY_FTYPE, multiPolygon.getEnvelope().getCentroid(), meta);
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
			
			meta.put(GeoJsonWriter.ORIGINAL_BBOX, bbox);
			
			for(Polygon p : polygons) {
				String n = getFilePrefix(p.getEnvelope().getCentroid().getX());
				if(attributes.containsKey("place")) {
					writeOut(GeoJsonWriter.featureAsGeoJSON(id, FeatureTypes.PLACE_BOUNDARY_FTYPE, attributes, p, meta), n);
				}
				else {
					writeOut(GeoJsonWriter.featureAsGeoJSON(id, FeatureTypes.ADMIN_BOUNDARY_FTYPE, attributes, p, meta), n);
				}
			}
		}
	}

	public synchronized void writeOut(String line, String n) {
		
		String fileName = this.dirPath + "/stripe" + n + ".gjson";
		try {
			File file = new File(fileName);
			if(!file.exists()) {
				file.createNewFile();
			}
			
			FileOutputStream fos = new FileOutputStream(file, true);
			PrintWriter printWriter = new PrintWriter(fos);
			printWriter.println(line);
			printWriter.flush();
			printWriter.close();
			
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
				Point ip = intersection.getCentroid();
				for(LineString split : GeometryUtils.split(l, ip.getCoordinate(), false)) {
					stripe(split, result);
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
	public void beforeLastRun() {
		
	}

	@Override
	public void afterLastRun() {
		executorService.shutdown();
		try {
			executorService.awaitTermination(1, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			log.error("Termination awaiting was interrupted", e);
		}
	}

	@Override
	public void handleAddrPoint(Map<String, String> attributes, Point point,
			JSONObject meta) {
		String id = GeoJsonWriter.getId(FeatureTypes.ADDR_POINT_FTYPE, point, meta);
		String n = getFilePrefix(point.getX());
		
		writeOut(GeoJsonWriter.featureAsGeoJSON(id, FeatureTypes.ADDR_POINT_FTYPE, attributes, point, meta), n);
	}

	public static String getFilePrefix(double x) {
		return String.format("%04d", (new Double((x + 180.0) * 10.0).intValue()));
	}

	@Override
	public void handlePlacePoint(Map<String, String> tags, Point pnt,
			JSONObject meta) {
		String fid = GeoJsonWriter.getId(FeatureTypes.PLACE_POINT_FTYPE, pnt, meta);
		String n = getFilePrefix(pnt.getX());
		writeOut(GeoJsonWriter.featureAsGeoJSON(fid, FeatureTypes.PLACE_POINT_FTYPE, tags, pnt, meta), n);
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
		writeOut(r.toString(), n);
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
		if(min == max) {
			String n = getFilePrefix(centroid.getX());
			writeOut(GeoJsonWriter.featureAsGeoJSON(fid, FeatureTypes.HIGHWAY_FEATURE_TYPE, way.tags, geometry, meta), n);
		}
		else if(max - min == 1) {
			//it's faster to write geometry as is in such case.
			for(int i = min; i <= max; i++) {
				String n = String.format("%04d", i); 
				writeOut(GeoJsonWriter.featureAsGeoJSON(fid, FeatureTypes.HIGHWAY_FEATURE_TYPE, way.tags, geometry, meta), n);
			}
		}
		else {
			List<LineString> segments = new ArrayList<>();
			stripe(geometry, segments);
			for(LineString stripe : segments) {
				String n = getFilePrefix(stripe.getCentroid().getX());
				writeOut(GeoJsonWriter.featureAsGeoJSON(fid, FeatureTypes.HIGHWAY_FEATURE_TYPE, way.tags, stripe, meta), n);
			}
		}
	}


}
