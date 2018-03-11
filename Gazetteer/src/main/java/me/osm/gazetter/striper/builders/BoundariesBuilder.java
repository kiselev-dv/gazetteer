package me.osm.gazetter.striper.builders;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
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

import me.osm.gazetter.Options;
import me.osm.gazetter.striper.BoundariesFallbacker;
import me.osm.gazetter.striper.FeatureTypes;
import me.osm.gazetter.striper.GeoJsonWriter;
import me.osm.gazetter.striper.builders.handlers.BoundariesHandler;
import me.osm.gazetter.striper.readers.PointsReader.Node;
import me.osm.gazetter.striper.readers.RelationsReader.Relation;
import me.osm.gazetter.striper.readers.RelationsReader.Relation.RelationMember;
import me.osm.gazetter.striper.readers.RelationsReader.Relation.RelationMember.ReferenceType;
import me.osm.gazetter.striper.readers.WaysReader.Way;
import me.osm.gazetter.utils.index.Accessor;
import me.osm.gazetter.utils.index.Accessors;
import me.osm.gazetter.utils.index.BinaryIndex;
import me.osm.gazetter.utils.index.IndexFactory;
import me.osm.gazetter.utils.index.BinaryIndex.IndexLineAccessMode;

public class BoundariesBuilder extends ABuilder {
	
	private static final Logger log = LoggerFactory.getLogger(BoundariesBuilder.class.getName());
	private static final GeometryFactory geometryFactory = new GeometryFactory();
	
	protected BoundariesHandler handler;
	private BoundariesFallbacker fallback = null;
	
	private static Set<String> ADMIN_LEVELS = new HashSet<>();
	static {
		ADMIN_LEVELS.addAll(Arrays.asList(new String[]{"1", "2", "3", "4", "5", "6", "7", "8", "9", "10"}));
	}
	
	//way rel role
	private BinaryIndex way2relation;

	//node way index
	private BinaryIndex node2way;

	private boolean byMemberOrdered = false;
	private boolean byNodeOrdered = false;

	private boolean byWayOrdered = false;
	
	private boolean indexFilled = false;
	
	private final BlockingQueue<Runnable> boundariesBuildQueue = 
			new LinkedBlockingQueue<Runnable>(Options.get().getNumberOfThreads() * 2);
	private final ExecutorService executorService = new ThreadPoolExecutor(
			Options.get().getNumberOfThreads(), 
			Options.get().getNumberOfThreads(),
            100L, 
            TimeUnit.MILLISECONDS,
            boundariesBuildQueue,
            new ThreadPoolExecutor.CallerRunsPolicy());
	
	private static final Accessor n2wWayAccessor = Accessors.longAccessor(8);
	private static final Accessor n2wNodeAccessor = Accessors.longAccessor(0);
	private static final Accessor w2rWayAccessor = Accessors.longAccessor(0);
	
	public BoundariesBuilder(BoundariesHandler handler, IndexFactory indexFactory, 
			BoundariesFallbacker fallback) {
		this.fallback = fallback;
		this.handler = handler;
		
		way2relation = indexFactory.newByteIndex(8);
		node2way = indexFactory.newByteIndex(8 + 8 + 4 + 8 + 8);
	}
	
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
				try {
					executorService.execute(new Task(rel, this));
				}
				catch (Exception e) {
					log.error("Failed to submit relation {} to polygon builder. Queue size: {}", 
							rel.id, boundariesBuildQueue.size());
					throw e;
				}
			}
		}
	}

	private void handleAdminBoundaryRelation(Relation rel) {
		
		MultiPolygon geometry = buildRelationGeometry(rel);
		if(geometry != null) {
			JSONObject meta = getRelMeta(rel);
			doneRelation(rel, geometry, meta);
		}
		else if (fallback != null) {
			geometry = fallback.getGeometry("r" + rel.id);
			if(geometry != null) {
				log.warn("Load geometry for relation {}.", rel.id);
				
				JSONObject meta = getRelMeta(rel);
				doneRelation(rel, geometry, meta);
			}
			else {
				log.warn("Failed to build geometry for relation {}.", rel.id);
			}
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
		
		saveBoundary(featureWithoutGeometry, geometry);
		handler.handleBoundary(featureWithoutGeometry, geometry);
	}

	protected static JSONObject getRelMeta(Relation rel) {
		
		JSONObject meta = new JSONObject();
		
		meta.put("id", rel.id);
		meta.put("type", "relation");

		return meta;
	}

	private MultiPolygon buildRelationGeometry(final Relation rel) {
			
		orderByWay();
		
		int relOuters = 0;
		int relInners = 0;
		
		List<LineString> outers = new ArrayList<>();
		List<LineString> inners = new ArrayList<>();
		
		List<String> notFound = new ArrayList<>();
		
		for(final RelationMember m : rel.members) {
			if(m.type == ReferenceType.WAY) {
				
				if(isInnerRole(m)) {
					relInners++;
				}
				else if (isOuterRole(m)){
					relOuters++;
				}
				else {
					// Ignore members with undefined role
					continue;
				}
				
				int wi = node2way.find(m.ref, n2wWayAccessor, IndexLineAccessMode.UNLINKED);

				List<ByteBuffer> points = node2way.findAll(wi, m.ref, n2wWayAccessor, IndexLineAccessMode.UNLINKED);
				Collections.sort(points, new Comparator<ByteBuffer>() {
					@Override
					public int compare(ByteBuffer bb1, ByteBuffer bb2) {
						return Integer.compare(bb1.getInt(16), bb2.getInt(16));
					}
				});
				
				if(!points.isEmpty() && points.size() >= 2) {
					List<Coordinate> coords = new ArrayList<Coordinate>();
					for(ByteBuffer bb : points) {
						double lon = bb.getDouble(20);
						double lat = bb.getDouble(28);
						
						if(lon != 0.0 && lat != 0.0) {
							coords.add(new Coordinate(lon, lat));
						}
					}

					if(coords.size() >= 2) {
						LineString ls = geometryFactory.createLineString(coords.toArray(new Coordinate[coords.size()]));
						if(isInnerRole(m)) {
							inners.add(ls);
						}
						else if (isOuterRole(m)){
							outers.add(ls);
						}
					}
					else {
						notFound.add(String.valueOf(m.ref));
					}
				}
				else {
					notFound.add(String.valueOf(m.ref));
				}
			}
		}
		
		if (relOuters == outers.size() && relInners == inners.size()) {
			return BuildUtils.buildMultyPolygon(log, rel, outers, inners);
		}
		else {
			
			log.warn("Some memmbers of admin boundary relation {} ({}) are missed.\n{}", 
					rel.id, 
					rel.tags.get("name"), 
					StringUtils.join(notFound, ", "));
			
			return null;
		}
		
	}

	private boolean isOuterRole(final RelationMember m) {
		return StringUtils.stripToNull(m.role) == null || "outer".equals(m.role);
	}

	private static boolean isInnerRole(final RelationMember m) {
		return "inner".equals(m.role);
	}

	private Coordinate[] buildWayGeometry(Way line) {
		orderByWay();
		
		int wayIndex = node2way.find(line.id, n2wWayAccessor, IndexLineAccessMode.UNLINKED);
		List<ByteBuffer> nodes = node2way.findAll(wayIndex, line.id, n2wWayAccessor, IndexLineAccessMode.UNLINKED);
		
		return BuildUtils.buildWayGeometry(line, nodes, 0, 20, 28);
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
		else if(isInnerRole(m) || "enclave".equals(m.role)) {
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
			int i = way2relation.find(line.id, w2rWayAccessor, IndexLineAccessMode.UNLINKED);
			if (i >= 0 || (line.isClosed() && filterByTags(line.tags))) {
				addWayToIndex(line);
			}
		}
		else if (line.isClosed() && filterByTags(line.tags)) {
			Coordinate[] wayGeometry = buildWayGeometry(line);
			
			if(wayGeometry != null && wayGeometry.length > 3) {
				try {
					LinearRing shell = geometryFactory.createLinearRing(wayGeometry);
					Polygon poly = geometryFactory.createPolygon(shell);
					MultiPolygon multiPolygon = geometryFactory.createMultiPolygon(new Polygon[]{poly});
					
					doneWay(line, multiPolygon);
				}
				catch (Exception e) {
					throw e;
				}
			}
			else {
				MultiPolygon geometry = null;
				if(fallback != null) {
					geometry = fallback.getGeometry("w" + line.id);
				}
				
				if(geometry != null) {
					log.warn("Load fallback geometry for way {}.", line.id);
					doneWay(line, geometry);
				}
				else {
					log.warn("Failed to build geometry for way {}.", line.id);
				}
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
		
		saveBoundary(featureWithoutGeometry, multiPolygon);
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
			way2relation.sort(Builder.FIRST_LONG_FIELD_COMPARATOR);
			byMemberOrdered = true;
		}
		log.info("Done read relations. {} ways addes to index.", way2relation.size());
	}

	@Override
	public void firstRunDoneWays() {
		if(!byNodeOrdered) {
			node2way.sort(Builder.FIRST_LONG_FIELD_COMPARATOR);
			byNodeOrdered = true;
			byWayOrdered = false;
		}
		log.info("Done read ways. {} nodes addes to index.", node2way.size());
	}

	public void orderByWay() {
		if(!byWayOrdered) {
			node2way.sort(Builder.SECOND_LONG_FIELD_COMPARATOR);
			byWayOrdered = true;
			byNodeOrdered = false;
		}
	}

	@Override
	public void handle(Node node) {
		
		int i = node2way.find(node.id, n2wNodeAccessor, IndexLineAccessMode.LINKED);		
		List<ByteBuffer> lines = node2way.findAll(i, node.id, n2wNodeAccessor, IndexLineAccessMode.LINKED);
		
		for(ByteBuffer bb : lines) {
			bb.putDouble(20, node.lon).putDouble(28, node.lat);
		}
		
		indexFilled = true;
	}

	@Override
	public void secondRunDoneRelations() {
		executorService.shutdown();
		try {
			while(!executorService.awaitTermination(1, TimeUnit.MINUTES)) {
				//wait untill done
			}
		} catch (InterruptedException e) {
			log.error("Awaiting for thread pull was terminated.", e);
			throw new RuntimeException(e);
		}
		finally {
			if(fallback != null) {
				fallback.close();
			}
			handler.freeThreadPool(getThreadPoolUser());
		}
	}
	
	private void saveBoundary(JSONObject feature,
			MultiPolygon geometry) {
		if(fallback != null) {
			this.fallback.saveBoundary(feature, geometry);
		}
	}

	@Override
	public void close() {
		way2relation.close();
		node2way.close();
	}
	
}
