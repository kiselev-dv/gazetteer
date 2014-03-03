package me.osm.gazetter.striper.builders;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import me.osm.gazetter.striper.readers.PointsReader.Node;
import me.osm.gazetter.striper.readers.RelationsReader.Relation;
import me.osm.gazetter.striper.readers.RelationsReader.Relation.RelationMember;
import me.osm.gazetter.striper.readers.RelationsReader.Relation.RelationMember.ReferenceType;
import me.osm.gazetter.striper.readers.WaysReader.Way;

import org.json.JSONArray;
import org.json.JSONObject;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.io.WKTWriter;
import com.vividsolutions.jts.operation.polygonize.Polygonizer;

public class BoundariesBuilder extends ABuilder {
	
	public static interface BoundariesHandler {
		public void handleBoundary(Map<String, String> attributes, MultiPolygon multiPolygon, JSONObject meta);
		void beforeLastRun();
		void afterLastRun();
	}

	private BoundariesHandler handler;
	
	public BoundariesBuilder(BoundariesHandler handler) {
		this.handler = handler;
	}
	
	private static Set<String> ADMIN_LEVELS = new HashSet<>();
	static {
		ADMIN_LEVELS.addAll(Arrays.asList(new String[]{"1", "2", "3", "4", "5", "6", "7", "8", "9", "10"}));
	}
	
	//way rel role
	private static final List<ByteBuffer> way2relation = new ArrayList<>(10000);

	//node way index
	private static final List<ByteBuffer> node2way = new ArrayList<>(10000);

	private boolean byMemberOrdered = false;
	private boolean byNodeOrdered = false;

	private boolean byWayOrdered = false;
	private GeometryFactory geometryFactory = new GeometryFactory();
	private boolean indexFilled = false;
	
	private static final ExecutorService executorService = Executors.newFixedThreadPool(4);
	
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
		if(isAdminBoundary(rel.tags)) {
			if(!isIndexFilled()) {
				addRelIndex(rel);
			}
			else {
				executorService.execute(new Task(rel, this));
				//handleAdminBoundaryRelation(rel);
			}
		}
	}

	private void handleAdminBoundaryRelation(Relation rel) {
		handler.handleBoundary(rel.tags, buildRelationGeometry(rel), getRelMeta(rel));
	}

	private JSONObject getRelMeta(Relation rel) {
		
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
		
		if(!lines.isEmpty()) {
			Polygonizer polygonizer = new Polygonizer();
			polygonizer.add(lines);
			
			try	{
				@SuppressWarnings("unchecked")
				Collection<Polygon> polygons = polygonizer.getPolygons();
				if(!polygons.isEmpty()) {
					Polygon[] ps =  polygons.toArray(new Polygon[polygons.size()]);
					MultiPolygon mp = geometryFactory.createMultiPolygon(ps);
					if(mp.isValid())
						return mp;
				}
			}
			catch (Exception e) {
				System.err.println("Cant polygonize:");
				WKTWriter wktWriter = new WKTWriter();
				for(LineString ls : lines) {
					System.err.println(wktWriter.write(ls));
				}
				e.printStackTrace(System.err);
			}
			
		}
		
		return null;
	}

	private Coordinate[] buildWayGeometry(Way line) {
		orderByWay();
		
		int wayIndex = findWay(line.id);
		List<ByteBuffer> nodes = findAll(node2way, wayIndex, line.id, 8);
		
		int c = 0;
		if(!nodes.isEmpty()) {
			Coordinate[] geometry = new Coordinate[line.nodes.size()];
			Collections.sort(nodes, Builder.FIRST_LONG_FIELD_COMPARATOR);
			
			for(final long n : line.nodes) {
				int ni = Collections.binarySearch(nodes, null, new Comparator<ByteBuffer>() {
					@Override
					public int compare(ByteBuffer bb, ByteBuffer key) {
						return Long.compare(bb.getLong(0), n);
					}
				});
				
				if(ni >= 0) {
					ByteBuffer node = nodes.get(ni);
					double lon = node.getDouble(20);
					double lat = node.getDouble(28);
					
					geometry[c++] = new Coordinate(lon, lat);
				}
			}
			return geometry;
		}
		
		return null;
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

	private Boolean isOuter(RelationMember m) {
		Boolean outer = null;
		if("outer".equals(m.role) || "".equals(m.role) || m.role == null || "exclave".equals(m.role))	{
			outer = true;
		}
		else if("inner".equals(m.role) || "enclave".equals(m.role)) {
			outer = false;
		}
		return outer;
	}

	private boolean isAdminBoundary(Map<String, String> tags) {
		return ("administrative".equals(tags.get("boundary")) 
					&& ADMIN_LEVELS.contains(tags.get("admin_level"))
					&& (tags.get("maritime") == null || !tags.get("maritime").equals("yes"))) 
				|| tags.containsKey("place"); 
	}

	@Override
	public void handle(Way line) {
		
		if(!isIndexFilled()) {
			int i = getRelationMembership(line.id);
			if (i >= 0 || (line.isClosed() && isAdminBoundary(line.tags))) {
				addWayToIndex(line);
			}
		}
		else if (line.isClosed() && isAdminBoundary(line.tags)) {
			Coordinate[] wayGeometry = buildWayGeometry(line);
			
			if(wayGeometry != null && wayGeometry.length > 0) {
				
				LinearRing shell = geometryFactory.createLinearRing(wayGeometry);
				Polygon poly = geometryFactory.createPolygon(shell);
				MultiPolygon multiPolygon = geometryFactory.createMultiPolygon(new Polygon[]{poly});
				
				handler.handleBoundary(line.tags, multiPolygon, getWayMeta(line));
			}
			
		}
	}


	private JSONObject getWayMeta(Way line) {
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
		if(!byMemberOrdered) {
			Collections.sort(way2relation, Builder.FIRST_LONG_FIELD_COMPARATOR);
			byMemberOrdered = true;
		}
	}

	@Override
	public void firstRunDoneWays() {
		if(!byNodeOrdered) {
			Collections.sort(node2way, Builder.FIRST_LONG_FIELD_COMPARATOR);
			byNodeOrdered = true;
			byWayOrdered = false;
		}
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
	public void beforeLastRun() {
		handler.beforeLastRun();
	}

	@Override
	public void afterLastRun() {
		executorService.shutdown();
		try {
			executorService.awaitTermination(1, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace(System.err);
		}
		finally {
			handler.afterLastRun();
		}
	}
	
}
