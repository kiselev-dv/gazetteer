package me.osm.gazetter.striper.builders;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import me.osm.gazetter.striper.readers.PointsReader.Node;
import me.osm.gazetter.striper.readers.RelationsReader.Relation;
import me.osm.gazetter.striper.readers.WaysReader.Way;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;

public class HighwaysBuilder extends ABuilder {

	private static final Logger log = LoggerFactory
			.getLogger(HighwaysBuilder.class.getName());

	private static volatile ExecutorService executorService = Executors.newFixedThreadPool(4);
	
	private static final class BuildWayGeometryTask implements Runnable {

		private Way way;
		private HighwaysHandler handler;

		public BuildWayGeometryTask(Way way, HighwaysHandler handler) {
			this.way = way;
			this.handler = handler;
		}
		
		@Override
		public void run() {
			buildLine(way, this.handler);
		}
		
	} 

	private static final String HIGHWAY_TAG = "highway";
	private JunctionsHandler junctionsHandler;
	private HighwaysHandler highwaysHandler;

	private static final int LON_OFFSET = 8 + 8;
	private static final int LAT_OFFSET = 8 + 8 + 8;
	
	public HighwaysBuilder(HighwaysHandler highwaysHandler,
			JunctionsHandler junctionsHandler) {
		this.highwaysHandler = highwaysHandler;
		this.junctionsHandler = junctionsHandler;
	}

	public static interface HighwaysHandler extends FeatureHandler {
		public void handleHighway(LineString geometry, Way way);
	}

	public static interface JunctionsHandler extends FeatureHandler {
		public void handleJunction(Coordinate coordinates, long nodeID,
				List<Long> highways);
	}

	private static final List<ByteBuffer> node2way = new ArrayList<>();
	private boolean indexFilled = false;
	private boolean byWayOrdered = false;

	private boolean doneReadNodes = false;

	private static final GeometryFactory factory = new GeometryFactory();

	@Override
	public void handle(Relation rel) {
		// TODO: support routes and associated street relation
	}

	@Override
	public void handle(Way line) {
		if (isHighway(line) && isNamed(line)) {
			if (indexFilled) {
				if(!this.doneReadNodes) {
					doneReadNodes();
					this.doneReadNodes = true;
				}
				orderByWay();
				executorService.execute(new BuildWayGeometryTask(line, highwaysHandler));
			} else {
				short i = 0;
				for (Long id : line.nodes) {
					ByteBuffer bb = ByteBuffer.allocate(8 + 8 + 8 + 8 + 2);
					bb.putLong(0, id);
					bb.putLong(8, line.id);
					bb.putDouble(LON_OFFSET, Double.NaN);
					bb.putDouble(LAT_OFFSET, Double.NaN);
					bb.putShort(8 + 8 + 8 + 8, i++);
					node2way.add(bb);
				}
			}
		}
	}

	private void doneReadNodes() {
		log.info("Nodes coordinates loaded");
	}

	private boolean isHighway(Way line) {
		return line.tags.containsKey(HIGHWAY_TAG)
				&& !line.tags.get(HIGHWAY_TAG).equals("bus_stop")
				&& !line.tags.get(HIGHWAY_TAG).equals("platform");
	}

	private boolean isNamed(Way line) {
		return line.tags.containsKey("name");
	}

	private static void buildLine(final Way line, HighwaysHandler handler) {

		int li = Collections.binarySearch(node2way, null,
				new Comparator<ByteBuffer>() {
					@Override
					public int compare(ByteBuffer row, ByteBuffer key) {
						return Long.compare(row.getLong(8), line.id);
					}
				});

		List<ByteBuffer> nodeRows = findAll(node2way, li, line.id, 8);
		
		//sort by node
		Collections.sort(nodeRows, ABuilder.FIRST_LONG_FIELD_COMPARATOR);

		List<Coordinate> coords = new ArrayList<>(line.nodes.size());
		for (final long pid : line.nodes) {
			int ni = Collections.binarySearch(nodeRows, null,
					new Comparator<ByteBuffer>() {
						@Override
						public int compare(ByteBuffer row, ByteBuffer key) {
							return Long.compare(row.getLong(0), pid);
						}
					});
			if (ni >= 0) {
				ByteBuffer bb = nodeRows.get(ni);
				double lon = bb.getDouble(LON_OFFSET);
				double lat = bb.getDouble(LAT_OFFSET);
				if(!Double.isNaN(lon) && !Double.isNaN(lat)) {
					coords.add(new Coordinate(lon, lat));
				}
				else {
					log.error("node {} not found for way {}", pid, line.id);
				}
			} else {
				log.error("node {} not found for way {}", pid, line.id);
			}
		}

		if (handler != null) {
			if (coords.size() > 1) {
				LineString linestring = factory.createLineString(coords
						.toArray(new Coordinate[coords.size()]));
				handler.handleHighway(linestring, line);
			}
		}
	}

	private void orderByWay() {
		if (!byWayOrdered) {
			Collections.sort(node2way, ABuilder.SECOND_LONG_FIELD_COMPARATOR);
			byWayOrdered = true;
		}
	}

	@Override
	public void handle(final Node node) {
		
		int ni = Collections.binarySearch(node2way, null,
				new Comparator<ByteBuffer>() {
					@Override
					public int compare(ByteBuffer row, ByteBuffer key) {
						return Long.compare(row.getLong(0), node.id);
					}
				});

		List<ByteBuffer> nodeRows = findAll(node2way, ni, node.id, 0);
		for (ByteBuffer row : nodeRows) {
			row.putDouble(LON_OFFSET, node.lon);
			row.putDouble(LAT_OFFSET, node.lat);
		}
	}

	@Override
	public void firstRunDoneWays() {
		indexFilled = true;
		Collections.sort(node2way, ABuilder.FIRST_LONG_FIELD_COMPARATOR);
		
		this.highwaysHandler.newThreadpoolUser(getThreadPoolUser());
		this.junctionsHandler.newThreadpoolUser(getThreadPoolUser());
	}

	@Override
	public void secondRunDoneRelations() {
		if (this.junctionsHandler != null) {
			Collections.sort(node2way, ABuilder.FIRST_LONG_FIELD_COMPARATOR);
			findJunctions();
		}
		executorService.shutdown();
		
		try {
			executorService.awaitTermination(5, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		this.highwaysHandler.freeThreadPool(getThreadPoolUser());
		this.junctionsHandler.freeThreadPool(getThreadPoolUser());
	}

	private void findJunctions() {
		long nodeId = -1;
		double lon = 0;
		double lat = 0;
		List<Long> highways = new ArrayList<>();
		for (ByteBuffer bb : node2way) {
			long nid = bb.getLong(0);
			if (nid == nodeId) {
				highways.add(bb.getLong(8));
			} else {
				if (highways.size() > 1) {
					this.junctionsHandler.handleJunction(new Coordinate(lon,
							lat), nodeId, new ArrayList<>(highways));
				}
				highways.clear();
				highways.add(bb.getLong(8));
			}
			nodeId = nid;
			lon = bb.getDouble(LON_OFFSET);
			lat = bb.getDouble(LAT_OFFSET);
		}

	}

}
