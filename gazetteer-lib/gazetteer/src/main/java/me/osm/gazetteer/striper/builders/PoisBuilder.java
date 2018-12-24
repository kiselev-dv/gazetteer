package me.osm.gazetteer.striper.builders;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import me.osm.gazetteer.LOGMarkers;
import me.osm.gazetteer.striper.Slicer;
import me.osm.gazetteer.striper.readers.RelationsReader;
import me.osm.gazetteer.striper.readers.WaysReader;
import me.osm.gazetteer.utils.index.Accessors;
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

import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;
import me.osm.gazetteer.striper.GeoJsonWriter;
import me.osm.gazetteer.striper.builders.handlers.PoisHandler;
import me.osm.gazetteer.striper.readers.PointsReader.Node;
import me.osm.gazetteer.utils.index.Accessor;
import me.osm.gazetteer.utils.index.BinaryIndex;
import me.osm.gazetteer.utils.index.IndexFactory;
import me.osm.osmdoc.model.Feature;
import me.osm.osmdoc.read.DOCFileReader;
import me.osm.osmdoc.read.DOCFolderReader;
import me.osm.osmdoc.read.DOCReader;
import me.osm.osmdoc.read.OSMDocFacade;
import me.osm.osmdoc.read.TagsDecisionTree;

public class PoisBuilder extends ABuilder {

	private TagsDecisionTree tagsFilter;

	private static final GeometryFactory factory = new GeometryFactory();

	private PoisHandler handler;

	private Set<String> named;

	private DOCReader reader;

	private static final Logger log = LoggerFactory.getLogger(PoisBuilder.class.getName());

	private BinaryIndex way2relation;
	private BinaryIndex node2way;

	//Trying to save some memory
	private TLongList writedAddrNodes = new TLongArrayList();

	private boolean indexFilled = false;
	private boolean orderedByway = false;
	private boolean byRealtionOrdered = false;

	private static final boolean fullGeometry = true;
	private static final long MASK_16_BITS = 0xFFFFL;

	private static final Accessor w2rRelAccessor = Accessors.longAccessor(8);
	private static final Accessor w2rWayAccessor = Accessors.longAccessor(0);
	private static final Accessor n2wWayAccessor = Accessors.longAccessor(8);
	private static final Accessor n2wNodeAccessor = Accessors.longAccessor(0);

	public PoisBuilder(PoisHandler handler, IndexFactory indexFactory,
			String catalogFolder, List<String> exclude, List<String> named) {
		this.handler = handler;

		if(catalogFolder.endsWith(".xml") || catalogFolder.equals("jar")) {
			reader = new DOCFileReader(catalogFolder);
		}
		else {
			reader = new DOCFolderReader(catalogFolder);
		}

		OSMDocFacade osmDocFacade = new OSMDocFacade(reader, exclude);

		this.tagsFilter = osmDocFacade.getPoiClassificator();

		this.named = new HashSet<>();
		for(Feature f : osmDocFacade.getBranches(named)) {
			this.named.add(f.getName());
		}

		way2relation = indexFactory.newByteIndex(8 + 8);
		node2way = indexFactory.newByteIndex(8 + 8 + 2 + 8 + 8);
	}

	@Override
	public void handle(final RelationsReader.Relation rel) {
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
		Set<String> type = tagsFilter.getType(tags);
		boolean typeFound = !type.isEmpty();
		if(typeFound && named.containsAll(type)) {
			return tags.containsKey("name");
		}
		return typeFound;
	}

	private void buildAddrPoint4Relation(final RelationsReader.Relation rel) {

		int i = way2relation.find(rel.id, w2rRelAccessor);

		if(i < 0) {
			return;
		}

		Point centroid = null;
		List<LineString> lines = new ArrayList<>();

		for(ByteBuffer bb : way2relation.findAll(i, rel.id, w2rRelAccessor)) {
			final long way = bb.getLong(0);

			int p = node2way.find(way, n2wWayAccessor);

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
					log.warn("Failed to build geometry for relation {}. No points found.", rel.id);
					return;
				}

				if(coords.size() >= 2) {
					LineString l = factory.createLineString(coords.toArray(new Coordinate[coords.size()]));
					lines.add(l);
				}
				else {
					log.warn("Wrong geometry rel {}, way {}", rel.id, way);
					centroid = factory.createPoint(new Coordinate(coords.get(0).x, coords.get(0).y));
				}
			}
			else {
				for(ByteBuffer bb2 : node2way.findAll(p, way, n2wWayAccessor)) {
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

			MultiPolygon mp = BuildUtils.buildMultyPolygon(log, rel, lines, null);
			if(mp != null && !mp.isEmpty() && mp.isValid()) {
				centroid = mp.getCentroid();

				Polygon polygon = (Polygon) mp.getGeometryN(0);
				meta.put(GeoJsonWriter.FULL_GEOMETRY, GeoJsonWriter.geometryToJSON(polygon));
			}
			else {
				centroid = lines.get(0).getCentroid();
			}
		}

		if(Double.isNaN(centroid.getCoordinate().x)
				|| Double.isNaN(centroid.getCoordinate().y)) {
			log.warn(LOGMarkers.E_INVALID_NAN_POI_PNT,
					"Invalid centroid for poi point rel_id_osm({})", rel.id);
		}
		else {
			handler.handlePoi(tagsFilter.getType(rel.tags), rel.tags, centroid, meta);
		}

	}

	private void orderByRelation() {
		if(!this.byRealtionOrdered) {
			way2relation.sort(SECOND_LONG_FIELD_COMPARATOR);
			this.byRealtionOrdered = true;
		}
	}

	private void indexRelation(RelationsReader.Relation rel) {
		for (RelationsReader.Relation.RelationMember rm : rel.members) {
			if(rm.type == RelationsReader.Relation.RelationMember.ReferenceType.WAY && (StringUtils.isEmpty(rm.role) || "outer".equals(rm.role))) {
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
		way2relation.sort(FIRST_LONG_FIELD_COMPARATOR);
	}

	@Override
	public void handle(final WaysReader.Way line) {
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
					int nodeIndex = binarySearchWithMask(writedAddrNodes, nid);
					if(nodeIndex >= 0) {
						long addrNodeIdWithN = writedAddrNodes.get(nodeIndex);
						long n = addrNodeIdWithN & MASK_16_BITS;
						long addrNodeId = addrNodeIdWithN >> 16;
						this.handler.handlePoi2Building(
								Slicer.formatFilePrefix((int)n, 1), addrNodeId, line.id, line.tags);
					}
				}
			}
		}
	}

	private List<ByteBuffer> getWayPoints(final long lineId) {
		int i = node2way.find(lineId, n2wWayAccessor);

		List<ByteBuffer> points = node2way.findAll(i, lineId, n2wWayAccessor);
		Collections.sort(points, new Comparator<ByteBuffer>() {

			@Override
			public int compare(ByteBuffer o1, ByteBuffer o2) {
				return Short.compare(o1.getShort(8 + 8), o2.getShort(8 + 8));
			}

		});

		return points;
	}

	private void buildAddrPointForWay(final WaysReader.Way line) {
		int i = node2way.find(line.id, n2wWayAccessor);
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
					log.warn("Failed to build geometry for way {}. Some points wasn't found.", line.id);
					centroid = factory.createPoint(coords.get(0));
				}
				else if(coords.size() < 2) {
					log.warn("Failed to build geometry for way {}. Only one point founded.", line.id);
					centroid = factory.createPoint(coords.get(0));
				}
				else if(isClosed(line) && coords.size() >= 4) {
					LinearRing geom = factory.createLinearRing(coords.toArray(new Coordinate[coords.size()]));
					centroid = geom.getCentroid();
					Polygon p = factory.createPolygon(geom);
					if(p.isValid()) {
						meta.put(GeoJsonWriter.FULL_GEOMETRY, GeoJsonWriter.geometryToJSON(p));
					}
				}
				else {
					LineString geom = factory.createLineString(coords.toArray(new Coordinate[coords.size()]));
					centroid = geom.getCentroid();
					if(geom.isValid()) {
						meta.put(GeoJsonWriter.FULL_GEOMETRY, GeoJsonWriter.geometryToJSON(geom));
					}
				}

			}
			else {
				ByteBuffer bb = node2way.get(i);
				double lon = bb.getDouble(8 + 8 + 2);
				double lat = bb.getDouble(8 + 8 + 2 + 8);
				centroid = factory.createPoint(new Coordinate(lon, lat));
			}

			if(Double.isNaN(centroid.getCoordinate().x)
					|| Double.isNaN(centroid.getCoordinate().y)) {
				log.warn(LOGMarkers.E_INVALID_NAN_POI_PNT,
						"Invalid centroid for poi point way_id_osm({})", line.id);
			}
			else {
				handler.handlePoi(tagsFilter.getType(line.tags), line.tags, centroid, meta);
			}
		}

	}

	private boolean isClosed(final WaysReader.Way line) {
		return line.nodes.get(0).equals(line.nodes.get(line.nodes.size() - 1));
	}

	private void orderByWay() {
		if(!this.orderedByway) {
			node2way.sort(SECOND_LONG_FIELD_COMPARATOR);
			this.orderedByway = true;
		}
	}

	private void indexWay(WaysReader.Way line) {
		if(filterByTags(line.tags) || way2relation.find(line.id, w2rWayAccessor) >= 0) {
			indexLine(line);
		}
	}

	private void indexLine(WaysReader.Way line) {
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
		node2way.sort(FIRST_LONG_FIELD_COMPARATOR);
		log.info("Done read ways. {} nodes added to index.", node2way.size());
		this.indexFilled = true;
	}

	@Override
	public void handle(final Node node) {
		Set<String> types = tagsFilter.getType(node.tags);
		if(!types.isEmpty()) {
			JSONObject meta = new JSONObject();

			meta.put("id", node.id);
			meta.put("type", "node");
			Point point = factory.createPoint(new Coordinate(node.lon, node.lat));

			if(Double.isNaN(point.getCoordinate().x) || Double.isNaN(point.getCoordinate().y)) {
				log.warn(LOGMarkers.E_INVALID_NAN_POI_PNT,
						"Invalid centroid for poi point node_id_osm({})", node.id);
			}
			else {
				handler.handlePoi(types, node.tags, point, meta);

				short n = new Double((point.getX() + 180.0) * 10.0).shortValue();

				long nodeWithN = node.id;
				nodeWithN <<= 16;
				nodeWithN |= n;

				writedAddrNodes.add(nodeWithN);
			}
		}

		indexNode2Way(node);
	}

	@Override
	public void firstRunDoneNodes() {
		writedAddrNodes.sort();
	}

	private void indexNode2Way(final Node node) {

		int ni = node2way.find(node.id, n2wNodeAccessor);

		for(ByteBuffer bb : node2way.findAll(ni, node.id, n2wNodeAccessor)) {
			bb.putDouble(8 + 8 + 2, node.lon);
			bb.putDouble(8 + 8 + 2 + 8, node.lat);
		}
	}

	@Override
	public void secondRunDoneRelations() {
		handler.freeThreadPool(getThreadPoolUser());
	}

	@Override
	public void close() {
		way2relation.close();
		node2way.close();
	}

}
