package me.osm.gazetter.striper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import me.osm.gazetter.striper.builders.AddrPointsBuilder;
import me.osm.gazetter.striper.builders.PlacePointsBuilder;
import me.osm.gazetter.striper.builders.AddrPointsBuilder.AddrPointHandler;
import me.osm.gazetter.striper.builders.PlacePointsBuilder.PlacePointHandler;
import me.osm.gazetter.striper.builders.BoundariesBuilder;
import me.osm.gazetter.utils.GeometryUtils;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.io.WKTWriter;

public class Slicer implements BoundariesBuilder.BoundariesHandler, AddrPointHandler, PlacePointHandler {

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
				new PlacePointsBuilder(instance));
		
		System.err.println("Done in " + DurationFormatUtils.formatDurationHMS(new Date().getTime() - start));
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
					System.err.println("Couldn't slice " + meta.getString("type") + " " + meta.getLong("id") + " " + new WKTWriter().write(p));
				}
			}
			
			JSONArray slices = new JSONArray();
			for(Polygon p : polygons) {
				String n = getFilePrefix(p.getEnvelope().getCentroid().getX());
				slices.put(n);
			}
			meta.put(GeoJsonWriter.META_SLICES, slices);
			
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
		
		try {

			File file = new File(this.dirPath + "/stripe" + n + ".gjson");
			if(!file.exists()) {
				file.createNewFile();
			}
			
			FileOutputStream fos = new FileOutputStream(file, true);
			PrintWriter printWriter = new PrintWriter(fos);
			printWriter.println(line);
			printWriter.flush();
			printWriter.close();
			
		} catch (IOException e) {
			e.printStackTrace(System.err);
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
			e.printStackTrace(System.err);
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

}
