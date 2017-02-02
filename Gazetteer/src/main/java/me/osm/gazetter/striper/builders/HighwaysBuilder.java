package me.osm.gazetter.striper.builders;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;

import gnu.trove.map.TLongIntMap;
import gnu.trove.map.hash.TLongIntHashMap;
import me.osm.gazetter.LOGMarkers;
import me.osm.gazetter.Options;
import me.osm.gazetter.striper.builders.handlers.HighwaysHandler;
import me.osm.gazetter.striper.builders.handlers.JunctionsHandler;
import me.osm.gazetter.striper.readers.PointsReader.Node;
import me.osm.gazetter.striper.readers.RelationsReader.Relation;
import me.osm.gazetter.striper.readers.RelationsReader.Relation.RelationMember;
import me.osm.gazetter.striper.readers.RelationsReader.Relation.RelationMember.ReferenceType;
import me.osm.gazetter.striper.readers.WaysReader.Way;
import me.osm.gazetter.utils.index.Accessor;
import me.osm.gazetter.utils.index.Accessors;
import me.osm.gazetter.utils.index.BinaryIndex;
import me.osm.gazetter.utils.index.IndexFactory;

/**
 * Класс строит геометрию way'ев
 * */
public class HighwaysBuilder extends ABuilder implements HighwaysHandler {

	private static final Logger log = LoggerFactory
			.getLogger(HighwaysBuilder.class.getName());

	private static final ExecutorService executorService = 
			Executors.newFixedThreadPool(Options.get().getNumberOfThreads());
	
	private final class BuildWayGeometryTask implements Runnable {

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
	private TLongIntMap w2n = new TLongIntHashMap();

	private static final int LON_OFFSET = 8 + 8;
	private static final int LAT_OFFSET = 8 + 8 + 8;
	
	public HighwaysBuilder(HighwaysHandler highwaysHandler,
			JunctionsHandler junctionsHandler,
			IndexFactory indexFactory) {
		this.highwaysHandler = highwaysHandler;
		this.junctionsHandler = junctionsHandler;
		
		node2way = indexFactory.newByteIndex(8 + 8 + 8 + 8 + 2);
	}

	private BinaryIndex node2way;
	
	private boolean indexFilled = false;
	private boolean byWayOrdered = false;

	private boolean doneReadNodes = false;

	private static final GeometryFactory factory = new GeometryFactory();

	@Override
	public void handle(Relation rel) {
		if("associatedStreet".equals(rel.tags.get("type"))) {
			if(indexFilled) {
				buildAssociatedStreet(rel);
			}
			else {
				for(RelationMember m : rel.members) {
					if(m.type == ReferenceType.WAY && ("street".equals(m.role) || !("house".equals(m.role)))) {
						w2n.put(m.ref, -1);
					}
				}
			}
		}
	}

	private void buildAssociatedStreet(Relation rel) {
		List<Long> waysIds = new ArrayList<>();
		List<RelationMember> buildingsIds = new ArrayList<>();
		for(RelationMember m : rel.members) {
			if(m.type == ReferenceType.WAY && ("street".equals(m.role) || !("house".equals(m.role)))) {
				waysIds.add(m.ref);
			}
			else {
				buildingsIds.add(m);
			}
		}

		if(!buildingsIds.isEmpty()) {
			for(Long wid : waysIds) {
				int fromto = w2n.get(wid);
				if(fromto >= 0) {
					int max = fromto & 0x0000FFFF;
					int min = fromto >> 16;
					
					//drop suspiciously long relations
					if(Math.abs(max - min) < 10 ) {
						this.highwaysHandler.handleAssociatedStreet(
							min, max, waysIds, buildingsIds, rel.id, rel.tags);
					}
				}
				else {
					log.warn(LOGMarkers.E_NO_ASSOCIATED_STREET_FOUND, 
							"No streets found for associated street relation, rel_osm_id({})", rel.id);
				}
			}
		}
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
				executorService.execute(new BuildWayGeometryTask(line, this));
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

	private void buildLine(final Way line, HighwaysHandler handler) {

		Accessor lneIDAccessor = Accessors.longAccessor(8);
		int li = node2way.find(line.id, lneIDAccessor);
		
		List<ByteBuffer> nodeRows = node2way.findAll(li, line.id, lneIDAccessor);
		
		//sort by node
		Collections.sort(nodeRows, Builder.FIRST_LONG_FIELD_COMPARATOR);

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
					log.debug("node {} not found for way {}", pid, line.id);
				}
			} else {
				log.debug("node {} not found for way {}", pid, line.id);
			}
		}

		if (handler != null) {
			if (coords.size() > 1) {
				
				if(coords.size() == 2 && coords.get(0).equals(coords.get(1))) {
					log.warn(LOGMarkers.E_WAY_ONLY_TWO_EQAL_POINTS, 
							"Way has only two eqal poins. id_way_osm({})", line.id);
				}
				else {
					LineString linestring = factory.createLineString(coords
							.toArray(new Coordinate[coords.size()]));
					handler.handleHighway(linestring, line);
				}
				
			}
		}
	}

	private void orderByWay() {
		if (!byWayOrdered) {
			node2way.sort(Builder.SECOND_LONG_FIELD_COMPARATOR);
			byWayOrdered = true;
		}
	}

	@Override
	public void handle(final Node node) {
		
		Accessor nodeIdAccessor = Accessors.longAccessor(0);
		int ni = node2way.find(node.id, nodeIdAccessor); 
		
		List<ByteBuffer> nodeRows = node2way.findAll(ni, node.id, nodeIdAccessor);
		for (ByteBuffer row : nodeRows) {
			row.putDouble(LON_OFFSET, node.lon);
			row.putDouble(LAT_OFFSET, node.lat);
		}
	}

	@Override
	public void firstRunDoneWays() {
		indexFilled = true;
		node2way.sort(Builder.FIRST_LONG_FIELD_COMPARATOR);
		
		this.highwaysHandler.newThreadpoolUser(getThreadPoolUser());
		this.junctionsHandler.newThreadpoolUser(getThreadPoolUser());
	}

	@Override
	public void secondRunDoneRelations() {
		if (this.junctionsHandler != null) {
			node2way.sort(Builder.FIRST_LONG_FIELD_COMPARATOR);
			findJunctions();
		}
		executorService.shutdown();
		
		try {
			while(!executorService.awaitTermination(1, TimeUnit.MINUTES)) {
				//wait
			}
		} catch (InterruptedException e) {
			throw new RuntimeException("Executor service awaiting shutdown interrupted.");
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

	@Override
	public void freeThreadPool(String user) {
		//do nothing
	}

	@Override
	public void newThreadpoolUser(String user) {
		//do nothing
	}

	@Override
	public void handleHighway(LineString geometry, Way way) {
		
		if(w2n.containsKey(way.id)) {
			Envelope env = geometry.getEnvelopeInternal();
			short n = new Double((env.getMinX() + 180.0) * 10.0).shortValue();
			
			int fromto = n << 16;
			n = new Double((env.getMaxX() + 180.0) * 10.0).shortValue();
			
			fromto |= n;
			w2n.put(way.id, fromto);
		}
		
		highwaysHandler.handleHighway(geometry, way);
	}

	@Override
	public void handleAssociatedStreet(int minN, int maxN, List<Long> wayId,
			List<RelationMember> buildings, long relationId,
			Map<String, String> relAttributes) {
		//do nothing
	}

	@Override
	public void close() {
		node2way.close();
	}

}
