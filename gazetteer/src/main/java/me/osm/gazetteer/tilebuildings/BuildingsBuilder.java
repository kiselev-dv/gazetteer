package me.osm.gazetteer.tilebuildings;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;

import me.osm.gazetteer.striper.builders.ABuilder;
import me.osm.gazetteer.striper.builders.Builder;
import me.osm.gazetteer.striper.readers.PointsReader.Node;
import me.osm.gazetteer.striper.readers.RelationsReader.Relation;
import me.osm.gazetteer.striper.readers.RelationsReader.Relation.RelationMember;
import me.osm.gazetteer.striper.readers.RelationsReader.Relation.RelationMember.ReferenceType;
import me.osm.gazetteer.striper.readers.WaysReader.Way;
import me.osm.gazetteer.utils.index.Accessor;
import me.osm.gazetteer.utils.index.Accessors;
import me.osm.gazetteer.utils.index.BBListIndexFactory;
import me.osm.gazetteer.utils.index.BinaryIndex;
import me.osm.gazetteer.utils.index.IndexFactory;
import me.osm.gazetteer.utils.index.MMapIndexFactory;
import me.osm.gazetteer.utils.index.BinaryIndex.IndexLineAccessMode;

public class BuildingsBuilder extends ABuilder {

	private static final Logger log = LoggerFactory.getLogger(BuildingsBuilder.class.getName());
	private static final GeometryFactory geometryFactory = new GeometryFactory();

	private boolean indexFilled = false;

	private Buildings3dHandler handler;

	private BinaryIndex way2relation;
	private BinaryIndex node2relation;
	private BinaryIndex node2way;

	private static final Accessor w2rRelAccessor = Accessors.longAccessor(0);
	private static final Accessor n2wNodeAccessor = Accessors.longAccessor(0);
	private static final Accessor n2rNodeAccessor = Accessors.longAccessor(0);

	private static final Accessor n2wLineAccessor = Accessors.longAccessor(8);
	private static final Accessor w2rLineAccessor = Accessors.longAccessor(0);

	private static final Accessor n2rRelAccessor = Accessors.longAccessor(8);


	public BuildingsBuilder(File dataDir, boolean binaryIndexes,
			Buildings3dHandler handler) {

		this.handler = handler;
		IndexFactory indxfactory = binaryIndexes ?
				new MMapIndexFactory(dataDir) :
					new BBListIndexFactory();

		way2relation = indxfactory.newByteIndex(8 + 8);
		node2relation = indxfactory.newByteIndex(8 + 8 + 8 + 8);
		node2way = indxfactory.newByteIndex(8 + 8 + 2 + 8 + 8);
	}

	@Override
	public void secondRunDoneRelations() {
		way2relation.close();
		node2relation.close();
		node2way.close();
	}

	@Override
	public void close() {
		this.handler.close();
	}

	@Override
	public void handle(Relation rel) {
		if(!indexFilled ) {
			if(isApplicable(rel.tags)) {
				indexRelation(rel);
			}
		}
		else {
			if(isApplicable(rel.tags)) {
				buildRelation(rel);
			}
		}
	}

	private void buildRelation(Relation rel) {
		List<Point> relationPoints = new ArrayList<>();
		Map<Long, List<Point>> relationWays = new HashMap<>();

		int ri = node2relation.find(rel.id, n2rRelAccessor, IndexLineAccessMode.UNLINKED);
		if (ri >= 0) {
			List<ByteBuffer> nodeBB = node2relation.findAll(
					ri, rel.id, n2rRelAccessor, IndexLineAccessMode.UNLINKED);
			for (ByteBuffer bb : nodeBB) {
				double lon = bb.getDouble(8 + 8);
				double lat = bb.getDouble(8 + 8 + 8);
				Point p = geometryFactory.createPoint(new Coordinate(lon, lat));
				p.setUserData(bb.getLong(0));
				relationPoints.add(p);
			}
		}

		ri = way2relation.find(rel.id, w2rRelAccessor, IndexLineAccessMode.UNLINKED);
		if (ri >= 0) {
			List<ByteBuffer> wayBB = node2relation.findAll(
					ri, rel.id, w2rRelAccessor, IndexLineAccessMode.UNLINKED);
			for (ByteBuffer wbb : wayBB) {
				long wayId = wbb.getLong(0);
				List<ByteBuffer> wayPoints = getWayPoints(wayId);

				List<Point> coords = new ArrayList<>();
				for(ByteBuffer bb : wayPoints) {
					double lon = bb.getDouble(8 + 8 + 2);
					double lat = bb.getDouble(8 + 8 + 2 + 8);

					Point p = geometryFactory.createPoint(new Coordinate(lon, lat));
					p.setUserData(bb.getLong(0));
					coords.add(p);
				}

				relationWays.put(wayId, coords);
			}
		}

		this.handler.handleRelation(rel, relationWays, relationPoints);
	}

	private void indexRelation(Relation rel) {
		for (RelationMember rm : rel.members) {
			if (rm.type == ReferenceType.NODE) {
				ByteBuffer bb = ByteBuffer.allocate(8 + 8 + 8 + 8);
				bb.putLong(rm.ref).putLong(rel.id);
				node2relation.add(bb);
			}

			if (rm.type == ReferenceType.WAY) {
				ByteBuffer bb = ByteBuffer.allocate(8 + 8);
				bb.putLong(rm.ref).putLong(rel.id);
				way2relation.add(bb);
			}
		}
	}

	private boolean isApplicable(Map<String, String> tags) {
		return tags.containsKey("building")
				|| tags.containsKey("building:part")
				|| "building".equals(tags.get("type"));
	}

	@Override
	public void handle(Way line) {
		if (!indexFilled) {
			indexWay(line);
		} else if (line.isClosed() && isApplicable(line.tags)) {
			handleIndexedWay(line);
		}
	}

	private void handleIndexedWay(Way line) {
		int w2rIndex = way2relation.find(line.id, w2rLineAccessor, IndexLineAccessMode.UNLINKED);

		int i = node2way.find(line.id, n2wLineAccessor, IndexLineAccessMode.UNLINKED);
		if (i >= 0 || w2rIndex >= 0) {
			List<ByteBuffer> wayPoints = getWayPoints(line.id);
			List<Point> coords = new ArrayList<>();
			for(ByteBuffer bb : wayPoints) {
				double lon = bb.getDouble(8 + 8 + 2);
				double lat = bb.getDouble(8 + 8 + 2 + 8);

				Point p = geometryFactory.createPoint(new Coordinate(lon, lat));
				p.setUserData(bb.getLong(0));
				coords.add(p);
			}
			if (i >= 0) {
				this.handler.handleWay(line, coords);
			}
			// Parts and buildings will be writen anyway
			else if (!line.tags.containsKey("building")
					&& !line.tags.containsKey("building:part") ) {
				this.handler.saveRelationWay(line, coords);
			}
		}
	}

	@Override
	public void secondRunDoneWays() {
		// Sort by relation
		way2relation.sort(SECOND_LONG_FIELD_COMPARATOR);
		node2relation.sort(SECOND_LONG_FIELD_COMPARATOR);
	}

	private List<ByteBuffer> getWayPoints(final long lineId) {
		int i = node2way.find(lineId, n2wLineAccessor, IndexLineAccessMode.UNLINKED);

		List<ByteBuffer> points = node2way.findAll(
				i, lineId, n2wLineAccessor, IndexLineAccessMode.UNLINKED);
		Collections.sort(points, new Comparator<ByteBuffer>() {

			@Override
			public int compare(ByteBuffer o1, ByteBuffer o2) {
				return Short.compare(o1.getShort(8 + 8), o2.getShort(8 + 8));
			}

		});
		return points;
	}

	private void indexWay(Way line) {
		if(line.isClosed() && isApplicable(line.tags)) {
			indexLine(line);
		}
		else if (way2relation.find(line.id, w2rRelAccessor, IndexLineAccessMode.IGNORE) >= 0) {
			indexLine(line);
		}
	}

	private void indexLine(Way line) {
		short i = 0;
		for(long ln :line.nodes) {
			ByteBuffer bb = ByteBuffer.allocate(8 + 8 + 2 + 8 + 8);
			bb.putLong(ln).putLong(line.id).putShort(i++);
			node2way.add(bb);
		}
	}

	@Override
	public void firstRunDoneRelations() {
		way2relation.sort(Builder.FIRST_LONG_FIELD_COMPARATOR);
		node2relation.sort(Builder.FIRST_LONG_FIELD_COMPARATOR);

		log.info("Done read relations. {} ways added to index.", way2relation.size());
	}

	@Override
	public void firstRunDoneWays() {
		node2way.sort(Builder.FIRST_LONG_FIELD_COMPARATOR);
		log.info("Done read ways. {} nodes added to index.", node2way.size());
	}


	@Override
	public void handle(Node node) {
		// Skip single node buildings,
		// keep only the referenced
		boolean indexed = false;
		indexed = indexNode2Way(node) || indexed;
		indexed = indexNode2Relation(node) || indexed;

		if (indexed && !node.tags.isEmpty()) {
			handler.saveNode(node);
		}
	}

	private boolean indexNode2Way(Node node) {
		int ni = node2way.find(
				node.id, n2wNodeAccessor, IndexLineAccessMode.LINKED);

		for(ByteBuffer bb : node2way.findAll(
				ni, node.id, n2wNodeAccessor, IndexLineAccessMode.LINKED)) {
			bb.putDouble(8 + 8 + 2, node.lon);
			bb.putDouble(8 + 8 + 2 + 8, node.lat);
		}

		return ni >= 0;
	}

	private boolean indexNode2Relation(Node node) {
		int ni = node2relation.find(node.id, n2wNodeAccessor, IndexLineAccessMode.LINKED);

		for(ByteBuffer bb : node2relation.findAll(
				ni, node.id, n2rNodeAccessor, IndexLineAccessMode.LINKED)) {
			bb.putDouble(8 + 8, node.lon);
			bb.putDouble(8 + 8 + 8, node.lat);
		}

		return ni >= 0;
	}

	@Override
	public void firstRunDoneNodes() {
		this.indexFilled = true;
		this.node2way.sort(SECOND_LONG_FIELD_COMPARATOR);
	}

}
