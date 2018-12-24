package me.osm.gazetteer.tilebuildings;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import me.osm.gazetteer.striper.Engine;
import me.osm.gazetteer.striper.readers.PointsReader;
import me.osm.gazetteer.striper.readers.RelationsReader;
import me.osm.gazetteer.striper.readers.WaysReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Point;

public class TileBuildings implements Buildings3dHandler {

	private static final Logger log = LoggerFactory.getLogger(TileBuildings.class);

	private int lvl;
	private File destFolder;
	private List<String> exclude = Collections.emptyList();
	private File dataDir;
	private boolean diskIndex;

	private Map<Long, PointsReader.Node> nodes;
	private Map<Long, WaysReader.Way> ways;
	private Map<TileNumber, OSMXMLWriter> writers;

	public TileBuildings(File dataDir, Integer lvl, File destFolder, List<String> exclude, boolean diskIndex) {
		this.lvl = lvl;
		this.destFolder = destFolder;
		this.dataDir = dataDir;
		this.exclude = exclude;
		this.diskIndex = diskIndex;

		this.nodes = new HashMap<>();
		this.ways = new HashMap<>();
		this.writers = new HashMap<>();
	}

	public void run() {
		Engine engine = new Engine();
		BuildingsBuilder builder = new BuildingsBuilder(this.dataDir, this.diskIndex, this);
		engine.filter(new HashSet<String>(exclude), dataDir.getPath(), builder);
		this.close();
		log.info("All done. {} tiles written.", this.writers.size());
	}

	@Override
	public void saveNode(PointsReader.Node node) {
		this.nodes.put(node.id, node);
	}

	@Override
	public void saveRelationWay(WaysReader.Way line, List<Point> coords) {
		if (!line.tags.isEmpty()) {
			this.ways.put(line.id, line);
		}
	}

	@Override
	public void handleWay(WaysReader.Way line, List<Point> coords) {
		Envelope bbox = new Envelope();
		for (Point p : coords) {
			bbox.expandToInclude(p.getEnvelopeInternal());
		}
		Coordinate centre = bbox.centre();
		TileNumber tileNumber = getTileNumber(centre.y, centre.x, this.lvl);
		OSMXMLWriter writer = getWriter(tileNumber);

		writeWayNodes(coords, writer);

		writer.writeWay(line.id, line.nodes, line.tags);

	}

	private void writeWayNodes(List<Point> coords, OSMXMLWriter writer) {
		for (Point p : coords) {
			long id = (long) p.getUserData();
			PointsReader.Node node = this.nodes.get(id);
			if (node != null) {
				writer.writeNode(id, p.getCoordinate().x, p.getCoordinate().y, node.tags);

			} else {
				writer.writeNode(id, p.getCoordinate().x, p.getCoordinate().y, null);
			}
		}
	}

	@Override
	public void handleRelation(RelationsReader.Relation rel, Map<Long, List<Point>> relationWays, List<Point> relationPoints) {
		Envelope bbox = new Envelope();

		// Count bbox only by outers/inners
		for (List<Point> pl : relationWays.values()) {
			for (Point p : pl) {
				bbox.expandToInclude(p.getEnvelopeInternal());
			}
		}

		Coordinate centre = bbox.centre();
		if (centre != null) {
			TileNumber tileNumber = getTileNumber(centre.y, centre.x, this.lvl);
			OSMXMLWriter writer = getWriter(tileNumber);

			writeWayNodes(relationPoints, writer);

			for (Entry<Long, List<Point>> pl : relationWays.entrySet()) {
				writeWayNodes(pl.getValue(), writer);
				long wayId = pl.getKey();
				WaysReader.Way way = ways.get(wayId);
				if (way != null) {
					writer.writeWay(wayId, getIds(pl.getValue()), way.tags);
				}
				else {
					writer.writeWay(wayId, getIds(pl.getValue()), null);
				}
			}

			writer.writeRelation(rel);
		}
	}

	private List<Long> getIds(List<Point> value) {
		List<Long> result = new ArrayList<>(value.size());
		for (Point p : value) {
			result.add((Long) p.getUserData());
		}
		return result;
	}

	@Override
	public void close() {
		for (OSMXMLWriter w : this.writers.values()) {
			w.close();
		}
	}

	private OSMXMLWriter getWriter(TileNumber tileNumber) {
		if (this.writers.get(tileNumber) == null) {

			OSMXMLWriter writer = new OSMXMLWriter(new File(tileNumber.toFilePath(this.destFolder.toString(), ".osm")));
			writer.writeBounds(tile2boundingBox(tileNumber));
			this.writers.put(tileNumber, writer);
		}

		return this.writers.get(tileNumber);
	}

	public static TileNumber getTileNumber(final double lat, final double lon, final int zoom) {
		int xtile = (int) Math.floor((lon + 180) / 360 * (1 << zoom));
		int ytile = (int) Math
				.floor((1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2
						* (1 << zoom));
		if (xtile < 0)
			xtile = 0;
		if (xtile >= (1 << zoom))
			xtile = ((1 << zoom) - 1);
		if (ytile < 0)
			ytile = 0;
		if (ytile >= (1 << zoom))
			ytile = ((1 << zoom) - 1);

		return new TileNumber(xtile, ytile, zoom);
	}

	Envelope tile2boundingBox(TileNumber t) {
		return new Envelope(tile2lon(t.x, t.z), tile2lon(t.x + 1, t.z), tile2lat(t.y, t.z), tile2lat(t.y + 1, t.z));
	}

	static double tile2lon(int x, int z) {
		return x / Math.pow(2.0, z) * 360.0 - 180;
	}

	static double tile2lat(int y, int z) {
		double n = Math.PI - (2.0 * Math.PI * y) / Math.pow(2.0, z);
		return Math.toDegrees(Math.atan(Math.sinh(n)));
	}

	private static final class TileNumber {
		int x;
		int y;
		int z;

		TileNumber(int x, int y, int z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == null) {
				return false;
			}

			if (obj instanceof TileNumber) {
				TileNumber that = (TileNumber) obj;
				return this.x == that.x && this.y == that.y && this.z == that.z;
			}

			return false;
		}

		@Override
		public int hashCode() {
			return (z + "/" + x + "/" + y).hashCode();
		}

		public String toFilePath(String prefix, String suffix) {
			return prefix +
					File.separator + z +
					File.separator + x +
					File.separator + y +
					suffix;
		}
	}

}
