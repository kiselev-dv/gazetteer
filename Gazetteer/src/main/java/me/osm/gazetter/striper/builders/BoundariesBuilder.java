package me.osm.gazetter.striper.builders;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import me.osm.gazetter.striper.FeatureTypes;
import me.osm.gazetter.striper.GeoJsonWriter;
import me.osm.gazetter.striper.builders.handlers.BoundariesHandler;
import me.osm.gazetter.striper.readers.PointsReader.Node;
import me.osm.gazetter.striper.readers.RelationsReader.Relation;
import me.osm.gazetter.striper.readers.RelationsReader.Relation.RelationMember;
import me.osm.gazetter.striper.readers.RelationsReader.Relation.RelationMember.ReferenceType;
import me.osm.gazetter.striper.readers.WaysReader.Way;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;

public class BoundariesBuilder extends ABuilder {
	
	private static final Logger log = LoggerFactory.getLogger(BoundariesBuilder.class.getName());
	private static final  GeometryFactory geometryFactory = new GeometryFactory();
	
	protected BoundariesHandler handler;
	
	public BoundariesBuilder(BoundariesHandler handler) {
		this.handler = handler;
	}
	
	private static Set<String> ADMIN_LEVELS = new HashSet<>();
	static {
		ADMIN_LEVELS.addAll(Arrays.asList(new String[]{"1", "2", "3", "4", "5", "6", "7", "8", "9", "10"}));
	}
	
	//way rel role
	private List<ByteBuffer> way2relation = new ArrayList<>(10000);

	//node way index
	private List<ByteBuffer> node2way = new ArrayList<>(10000);

	private boolean byMemberOrdered = false;
	private boolean byNodeOrdered = false;

	private boolean byWayOrdered = false;
	
	private boolean indexFilled = false;
	
	private final ExecutorService executorService = Executors.newFixedThreadPool(4);
	
	private static final class Task implements Runnable {

		private final Relation relation;
		private final BoundariesBuilder bb;
		
		public Task(Relation relation, BoundariesBuilder bb) {
			this.relation = relation;
			this.bb = bb;
		}
		
		@Override
		public void run() {
			bb.handleAdminBoundaryRelation(relation);
		}
		
	}
	
	@Override
	public void handle(Relation rel) {
		if(filterByTags(rel.tags)) {
			if(!isIndexFilled()) {
				addRelIndex(rel);
			}
			else {
				executorService.execute(new Task(rel, this));
			}
		}
	}

	private void handleAdminBoundaryRelation(Relation rel) {
		
		MultiPolygon geometry = buildRelationGeometry(rel);
		if(geometry != null) {
			JSONObject meta = getRelMeta(rel);
			doneRelation(rel, geometry, meta);
		}
		else {
			log.warn("Failed to build geometry for relation {}.", rel.id);
		}
	}

	protected void doneRelation(Relation rel, MultiPolygon geometry,
			JSONObject meta) {
		
		String fType = FeatureTypes.ADMIN_BOUNDARY_FTYPE; 
		Point originalCentroid = geometry.getEnvelope().getCentroid();
		String id = GeoJsonWriter.getId(fType, originalCentroid, meta);
		JSONObject featureWithoutGeometry = GeoJsonWriter.createFeature(id, fType, rel.tags, null, meta);
		
		assert GeoJsonWriter.getId(featureWithoutGeometry.toString()).equals(id) 
			: "Failed getId for " + featureWithoutGeometry.toString();
	
		assert GeoJsonWriter.getFtype(featureWithoutGeometry.toString()).equals(FeatureTypes.ADMIN_BOUNDARY_FTYPE) 
			: "Failed getFtype for " + featureWithoutGeometry.toString();
		
		handler.handleBoundary(featureWithoutGeometry, geometry);
	}

	protected static JSONObject getRelMeta(Relation rel) {
		
		JSONObject meta = new JSONObject();
		
		meta.put("id", rel.id);
		meta.put("type", "relation");

		JSONArray outers = new JSONArray();
		JSONArray inners = new JSONArray();
		
		for(RelationMember rm : rel.members) {
			if(rm.type == ReferenceType.WAY) {
				 Boolean outer = isOuter(rm);
				 
				 if(outer != null) {
					 if (outer) 
						 outers.put(rm.ref);
					 else
						 inners.put(rm.ref);
				 } 
			}
		}
		
		meta.put("outers", outers);
		meta.put("inners", inners);
		
		return meta;
	}

	private MultiPolygon buildRelationGeometry(final Relation rel) {
			
		orderByWay();
		
		List<LineString> lines = new ArrayList<>();
		
		for(final RelationMember m : rel.members) {
			if(m.type == ReferenceType.WAY) {
				int wi = Collections.binarySearch(node2way, null, new Comparator<ByteBuffer>() {
					@Override
					public int compare(ByteBuffer bb, ByteBuffer key) {
						return Long.compare(bb.getLong(8), m.ref);
					}
				});

				List<ByteBuffer> points = findAll(node2way, wi, m.ref, 8);
				Collections.sort(points, new Comparator<ByteBuffer>() {
					@Override
					public int compare(ByteBuffer bb1, ByteBuffer bb2) {
						return Integer.compare(bb1.getInt(16), bb2.getInt(16));
					}
				});
				
				if(!points.isEmpty()) {
					Coordinate[] coords = new Coordinate[points.size()];
					int i = 0;
					for(ByteBuffer bb : points) {
						double lon = bb.getDouble(20);
						double lat = bb.getDouble(28);
						
						coords[i++] = new Coordinate(lon, lat);
					}
					
					lines.add(geometryFactory.createLineString(coords));
				}
			}
		}
		
		return BuildUtils.buildMultyPolygon(rel, lines);
	}

	private Coordinate[] buildWayGeometry(Way line) {
		orderByWay();
		
		int wayIndex = findWay(line.id);
		List<ByteBuffer> nodes = findAll(node2way, wayIndex, line.id, 8);
		
		return BuildUtils.buildWayGeometry(line, nodes, 0, 20, 28);
	}

	private int findWay(final long id) {
		return Collections.binarySearch(node2way, null, new Comparator<ByteBuffer>() {

			@Override
			public int compare(ByteBuffer bb, ByteBuffer key) {
				return Long.compare(bb.getLong(8), id);
			}
		});
	}

	private boolean isIndexFilled() {
		return indexFilled;
	}
	
	private void addRelIndex(Relation rel) {
		for(RelationMember m : rel.members){
			if(m.type == ReferenceType.WAY) {
				Boolean outer = isOuter(m);
				
				if(outer != null) {
					ByteBuffer bb = ByteBuffer.allocate(8);
					bb.putLong(m.ref);
					
					way2relation.add(bb);
				}
			}
		}
	}

	protected static Boolean isOuter(RelationMember m) {
		Boolean outer = null;
		if("outer".equals(m.role) || "".equals(m.role) || m.role == null || "exclave".equals(m.role))	{
			outer = true;
		}
		else if("inner".equals(m.role) || "enclave".equals(m.role)) {
			outer = false;
		}
		return outer;
	}

	protected boolean filterByTags(Map<String, String> tags) {
		return ("administrative".equals(tags.get("boundary")) 
					&& ADMIN_LEVELS.contains(tags.get("admin_level"))
					&& (tags.get("maritime") == null || !tags.get("maritime").equals("yes"))); 
	}

	@Override
	public void handle(Way line) {
		
		if(!isIndexFilled()) {
			int i = getRelationMembership(line.id);
			if (i >= 0 || (line.isClosed() && filterByTags(line.tags))) {
				addWayToIndex(line);
			}
		}
		else if (line.isClosed() && filterByTags(line.tags)) {
			Coordinate[] wayGeometry = buildWayGeometry(line);
			
			if(wayGeometry != null && wayGeometry.length > 0) {
				
				LinearRing shell = geometryFactory.createLinearRing(wayGeometry);
				Polygon poly = geometryFactory.createPolygon(shell);
				MultiPolygon multiPolygon = geometryFactory.createMultiPolygon(new Polygon[]{poly});
				
				doneWay(line, multiPolygon);
			}
		}
	}

	protected void doneWay(Way line, MultiPolygon multiPolygon) {
		
		String fType = FeatureTypes.ADMIN_BOUNDARY_FTYPE; 
		Point originalCentroid = multiPolygon.getEnvelope().getCentroid();
		JSONObject meta = getWayMeta(line);
		String id = GeoJsonWriter.getId(fType, originalCentroid, meta);
		JSONObject featureWithoutGeometry = GeoJsonWriter.createFeature(id, fType, line.tags, null, meta);
		
		assert GeoJsonWriter.getId(featureWithoutGeometry.toString()).equals(id) 
			: "Failed getId for " + featureWithoutGeometry.toString();

		assert GeoJsonWriter.getFtype(featureWithoutGeometry.toString()).equals(FeatureTypes.ADMIN_BOUNDARY_FTYPE) 
			: "Failed getFtype for " + featureWithoutGeometry.toString();
		
		handler.handleBoundary(featureWithoutGeometry, multiPolygon);
	}


	protected JSONObject getWayMeta(Way line) {
		JSONObject result = new JSONObject();
		
		result.put("id", line.id);
		result.put("type", "way");
		
		return result;
	}

	private void addWayToIndex(Way line) {
		int i = 0;
		for(long node : line.nodes) {
			ByteBuffer bb = ByteBuffer.allocate(8 + 8 + 4 + 8 + 8);
			bb.putLong(0, node);
			bb.putLong(8, line.id);
			bb.putInt(16, i++);
			
			node2way.add(bb);
		}
	}

	@Override
	public void firstRunDoneRelations()	{
		handler.newThreadpoolUser(getThreadPoolUser());
		if(!byMemberOrdered) {
			Collections.sort(way2relation, Builder.FIRST_LONG_FIELD_COMPARATOR);
			byMemberOrdered = true;
		}
		log.info("Done read relations. {} ways addes to index.", way2relation.size());
	}

	@Override
	public void firstRunDoneWays() {
		if(!byNodeOrdered) {
			Collections.sort(node2way, Builder.FIRST_LONG_FIELD_COMPARATOR);
			byNodeOrdered = true;
			byWayOrdered = false;
		}
		log.info("Done read ways. {} nodes addes to index.", node2way.size());
	}

	public void orderByWay() {
		if(!byWayOrdered) {
			Collections.sort(node2way, Builder.SECOND_LONG_FIELD_COMPARATOR);
			byWayOrdered = true;
			byNodeOrdered = false;
		}
	}

	@Override
	public void handle(Node node) {
		int i = findNode(node.id);
		List<ByteBuffer> lines = findAll(node2way, i, node.id, 0);
		
		for(ByteBuffer bb : lines) {
			bb.putDouble(20, node.lon).putDouble(28, node.lat);
		}
		
		indexFilled = true;
	}

	private int getRelationMembership(final long id) {
		return Collections.binarySearch(way2relation, null, new Comparator<ByteBuffer>() {
			
			@Override
			public int compare(ByteBuffer bb, ByteBuffer key) {
				return Long.compare(bb.getLong(0), id);
			}
		});
	}

	private int findNode(final long id) {
		return Collections.binarySearch(node2way, null, new Comparator<ByteBuffer>() {

			@Override
			public int compare(ByteBuffer bb, ByteBuffer key) {
				return Long.compare(bb.getLong(0), id);
			}
		});
	}

	@Override
	public void secondRunDoneRelations() {
		executorService.shutdown();
		try {
			executorService.awaitTermination(5, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			log.error("Awaiting for thread pull was terminated.", e);
		}
		finally {
			handler.freeThreadPool(getThreadPoolUser());
		}
	}
	
}
