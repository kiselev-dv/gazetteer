package me.osm.gazetter.striper.builders;

import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import me.osm.gazetter.striper.GeoJsonWriter;
import me.osm.gazetter.striper.builders.handlers.PoisHandler;
import me.osm.gazetter.striper.readers.PointsReader.Node;
import me.osm.gazetter.striper.readers.RelationsReader.Relation;
import me.osm.gazetter.striper.readers.RelationsReader.Relation.RelationMember;
import me.osm.gazetter.striper.readers.RelationsReader.Relation.RelationMember.ReferenceType;
import me.osm.gazetter.striper.readers.WaysReader.Way;
import me.osm.osmdoc.read.OSMDocFacade;
import me.osm.osmdoc.read.TagsDecisionTree;

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

public class PoisBuilder extends ABuilder {
	
	private TagsDecisionTree tagsFilter;

	private static final GeometryFactory factory = new GeometryFactory();

	private PoisHandler handler;
	
	public PoisBuilder(PoisHandler handler, String catalogFolder, List<String> exclude) {
		this.handler = handler;
		this.tagsFilter = new OSMDocFacade(catalogFolder, exclude).getPoiClassificator();
	}
	
	private static final Logger log = LoggerFactory.getLogger(PoisBuilder.class.getName());
	

	private List<ByteBuffer> way2relation = new ArrayList<>(); 
	private List<ByteBuffer> node2way = new ArrayList<>();

	//Trying to save some memory
	private TLongList writedAddrNodes = new TLongArrayList(); 
	
	private boolean indexFilled = false;
	private boolean orderedByway = false;
	private boolean byRealtionOrdered = false;
	
	private static final boolean fullGeometry = true;
	private static final long MASK_16_BITS = 0xFFFFL;
	
	@Override
	public void handle(final Relation rel) {
		if(!indexFilled) {
			if("multipolygon".equals(rel.tags.get("type")) && filterByTags(rel.tags)) {
				indexRelation(rel);
			}
		}
		else {
			orderByRelation();
			orderByWay();
			
			buildAddrPoint4Relation(rel);
		}
	}

	private boolean filterByTags(Map<String, String> tags) {
		return !tagsFilter.getType(tags).isEmpty();
	}

	private void buildAddrPoint4Relation(final Relation rel) {
		int i = Collections.binarySearch(way2relation, null, new Comparator<ByteBuffer>(){
			@Override
			public int compare(ByteBuffer bb, ByteBuffer key) {
				return Long.compare(bb.getLong(8), rel.id);
			}
		});

		if(i < 0) {
			return;
		}
		
		Point centroid = null;
		List<LineString> lines = new ArrayList<>();
		
		for(ByteBuffer bb : findAll(way2relation, i, rel.id, 8)) {
			final long way = bb.getLong(0);
			
			int p = Collections.binarySearch(node2way, null, new Comparator<ByteBuffer>(){
				@Override
				public int compare(ByteBuffer bb, ByteBuffer key) {
					return Long.compare(bb.getLong(8), way);
				}
			});

			if(fullGeometry) {
				List<ByteBuffer> wayPoints = getWayPoints(way);
				Collections.sort(wayPoints, new Comparator<ByteBuffer>() {
					@Override
					public int compare(ByteBuffer o1, ByteBuffer o2) {
						return Short.compare(o1.getShort(8 + 8), o2.getShort(8 + 8));
					}
				});
				List<Coordinate> coords = new ArrayList<>();
				for(ByteBuffer pbb : wayPoints) {
					double lon = pbb.getDouble(8 + 8 + 2);
					double lat = pbb.getDouble(8 + 8 + 2 + 8);
					coords.add(new Coordinate(lon, lat));
				}

				if(coords.isEmpty()) {
					log.error("Failed to build geometry for relation {}. No points found.", rel.id);
					return;
				}
				
				LineString l = factory.createLineString(coords.toArray(new Coordinate[coords.size()]));
				lines.add(l);
			}
			else {
				for(ByteBuffer bb2 : findAll(node2way, p, way, 8)) {
					double lon = bb2.getDouble(8 + 8 + 2);
					double lat = bb2.getDouble(8 + 8 + 2 + 8);
					centroid = factory.createPoint(new Coordinate(lon, lat));
				}
				break;
			}
		}

		JSONObject meta = new JSONObject();
		meta.put("id", rel.id);
		meta.put("type", "relation");
		
		if(fullGeometry) {
			if(lines.isEmpty()) {
				return;
			}
			
			MultiPolygon mp = BuildUtils.buildMultyPolygon(rel, lines);
			if(mp != null && !mp.isEmpty() && mp.isValid()) {
				centroid = mp.getCentroid();
				
				Polygon polygon = (Polygon) mp.getGeometryN(0);
				meta.put("fullGeometry", GeoJsonWriter.geometryToJSON(polygon));
			}
			else {
				centroid = lines.get(0).getCentroid();
			}
		}
		
		handler.handlePoi(tagsFilter.getType(rel.tags), rel.tags, centroid, meta);
	}

	private void orderByRelation() {
		if(!this.byRealtionOrdered) {
			Collections.sort(way2relation, Builder.SECOND_LONG_FIELD_COMPARATOR);
			this.byRealtionOrdered = true;
		}
	}

	private void indexRelation(Relation rel) {
		for (RelationMember rm : rel.members) {
			if(rm.type == ReferenceType.WAY && (StringUtils.isEmpty(rm.role) || "outer".equals(rm.role))) {
				ByteBuffer bb = ByteBuffer.allocate(8 + 8);
				bb.putLong(rm.ref).putLong(rel.id);
				way2relation.add(bb);
				
				if(!fullGeometry) {
					//one way (one point) will be enough
					break;
				} 
			}
		}
	}

	@Override
	public void firstRunDoneRelations() {
		handler.newThreadpoolUser(getThreadPoolUser());
		Collections.sort(way2relation, Builder.FIRST_LONG_FIELD_COMPARATOR);
	}
	
	@Override
	public void handle(final Way line) {
		if(!indexFilled) {
			indexWay(line);
		}
		else {
			orderByWay();
			if(filterByTags(line.tags)) {
				buildAddrPointForWay(line);
			}
			
			//Our poi node is a part of building contour
			if(line.isClosed() && line.tags.containsKey("building")) {
				for(int i = 0; i < line.nodes.size() - 1; i++) {
					long nid = line.nodes.get(i);
					int nodeIndex = AddrPointsBuilder.binarySearchWithMask(writedAddrNodes, nid);
					if(nodeIndex >= 0) {
						long addrNodeIdWithN = writedAddrNodes.get(nodeIndex);
						long n = addrNodeIdWithN & MASK_16_BITS;
						long addrNodeId = addrNodeIdWithN >> 16;
						this.handler.handlePoi2Building(String.format("%04d", n), addrNodeId, line.id, line.tags);
					}
				}
			}
		}
	}
	
	private List<ByteBuffer> getWayPoints(final long lineId) {
		int i = Collections.binarySearch(node2way, null, new Comparator<ByteBuffer>() {
			@Override
			public int compare(ByteBuffer row, ByteBuffer key) {
				return Long.compare(row.getLong(8), lineId);
			}
		});

		List<ByteBuffer> points = findAll(node2way, i, lineId, 8);
		Collections.sort(points, new Comparator<ByteBuffer>() {

			@Override
			public int compare(ByteBuffer o1, ByteBuffer o2) {
				return Short.compare(o1.getShort(8 + 8), o2.getShort(8 + 8));
			}
			
		});
		return points;
	}

	private void buildAddrPointForWay(final Way line) {
		int i = Collections.binarySearch(node2way, null, new Comparator<ByteBuffer>() {
			@Override
			public int compare(ByteBuffer row, ByteBuffer key) {
				return Long.compare(row.getLong(8), line.id);
			}
		});
		
		if(i >= 0) {
			JSONObject meta = new JSONObject();
			meta.put("id", line.id);
			meta.put("type", "way");

			Point centroid = null;
			if(fullGeometry) {
				List<ByteBuffer> wayPoints = getWayPoints(line.id);
				Collections.sort(wayPoints, new Comparator<ByteBuffer>() {
					@Override
					public int compare(ByteBuffer o1, ByteBuffer o2) {
						return Short.compare(o1.getShort(8 + 8), o2.getShort(8 + 8));
					}
				});
				List<Coordinate> coords = new ArrayList<>();
				for(ByteBuffer bb : wayPoints) {
					double lon = bb.getDouble(8 + 8 + 2);
					double lat = bb.getDouble(8 + 8 + 2 + 8);
					coords.add(new Coordinate(lon, lat));
				}
				
				if(coords.isEmpty()) {
					log.error("Failed to build geometry for way {}. No points found.", line.id);
					return;
				}
				
				if(coords.size() != line.nodes.size()) {
					log.error("Failed to build geometry for way {}. Some points wasn't found.", line.id);
					centroid = factory.createPoint(coords.get(0));
				}
				else if(coords.size() < 2) {
					log.error("Failed to build geometry for way {}. Only one point founded.", line.id);
					centroid = factory.createPoint(coords.get(0));
				}
				else if(line.nodes.get(0).equals(line.nodes.get(line.nodes.size() - 1))) {
					LinearRing geom = factory.createLinearRing(coords.toArray(new Coordinate[coords.size()]));
					centroid = geom.getCentroid();
					meta.put("fullGeometry", GeoJsonWriter.geometryToJSON(factory.createPolygon(geom)));
				}
				else {
					LineString geom = factory.createLineString(coords.toArray(new Coordinate[coords.size()]));
					centroid = geom.getCentroid();
					meta.put("fullGeometry", GeoJsonWriter.geometryToJSON(geom));
				}
				
			}
			else {
				ByteBuffer bb = node2way.get(i);
				double lon = bb.getDouble(8 + 8 + 2);
				double lat = bb.getDouble(8 + 8 + 2 + 8);
				centroid = factory.createPoint(new Coordinate(lon, lat));
			}
			
			handler.handlePoi(tagsFilter.getType(line.tags), line.tags, centroid, meta);
		}
		
	}

	private void orderByWay() {
		if(!this.orderedByway) {
			Collections.sort(node2way, Builder.SECOND_LONG_FIELD_COMPARATOR);
			this.orderedByway = true;
		}
	}

	private void indexWay(Way line) {
		if(filterByTags(line.tags) || findRelMemberIndex(line.id) >= 0) {
			indexLine(line);
		}
	}

	private void indexLine(Way line) {
		short i = 0;
		for(long ln :line.nodes) {
			ByteBuffer bb = ByteBuffer.allocate(8 + 8 + 2 + 8 + 8);
			bb.putLong(ln).putLong(line.id).putShort(i++);
			node2way.add(bb);
			
			if(!fullGeometry) {
				break;
			}
		}
	}

	@Override
	public void firstRunDoneWays() {
		Collections.sort(node2way, Builder.FIRST_LONG_FIELD_COMPARATOR);
		log.info("Done read ways. {} nodes added to index.", node2way.size());
		this.indexFilled = true;
	}

	private int findRelMemberIndex(final long id) {
		
		return Collections.binarySearch(way2relation, null, new Comparator<ByteBuffer>() {

			@Override
			public int compare(ByteBuffer obj, ByteBuffer key) {
				return Long.compare(obj.getLong(0), id);
			}
		});
	}

	@Override
	public void handle(final Node node) {
		Set<String> types = tagsFilter.getType(node.tags);
		if(!types.isEmpty()) {
			JSONObject meta = new JSONObject();
			
			meta.put("id", node.id);
			meta.put("type", "node");
			Point point = factory.createPoint(new Coordinate(node.lon, node.lat));
			handler.handlePoi(types, node.tags, point, meta);
			
			short n = new Double((point.getX() + 180.0) * 10.0).shortValue();
			
			long nodeWithN = node.id;
			nodeWithN <<= 16;
			nodeWithN |= n;
			
			writedAddrNodes.add(nodeWithN);
		}
		
		indexNode2Way(node);
	}
	
	@Override
	public void firstRunDoneNodes() {
		writedAddrNodes.sort();
	}

	private void indexNode2Way(final Node node) {
		int ni = Collections.binarySearch(node2way, null, new Comparator<ByteBuffer>() {

			@Override
			public int compare(ByteBuffer row, ByteBuffer key) {
				return Long.compare(row.getLong(0), node.id);
			}
		});
		
		for(ByteBuffer bb : findAll(node2way, ni, node.id, 0)) {
			bb.putDouble(8 + 8 + 2, node.lon);
			bb.putDouble(8 + 8 + 2 + 8, node.lat);
		}
	}
	
	@Override
	public void secondRunDoneRelations() {
		handler.freeThreadPool(getThreadPoolUser());
	}

}
