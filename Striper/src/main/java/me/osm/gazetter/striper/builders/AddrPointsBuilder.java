package me.osm.gazetter.striper.builders;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.osm.gazetter.striper.readers.PointsReader.Node;
import me.osm.gazetter.striper.readers.RelationsReader.Relation;
import me.osm.gazetter.striper.readers.RelationsReader.Relation.RelationMember;
import me.osm.gazetter.striper.readers.RelationsReader.Relation.RelationMember.ReferenceType;
import me.osm.gazetter.striper.readers.WaysReader.Way;
import me.osm.gazetter.utils.LocatePoint;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;

public class AddrPointsBuilder extends ABuilder {
	
	private static final String ADDR_STREET = "addr:street";

	public static interface AddrPointHandler {
		public void handleAddrPoint(Map<String, String> attributes, Point point, JSONObject meta);
		void beforeLastRun();
		void afterLastRun();
	}
	
	private static final String ADDR_INTERPOLATION = "addr:interpolation";
	private static final String ADDR_HOUSENUMBER = "addr:housenumber";

	private List<ByteBuffer> way2relation = new ArrayList<>(); 
	private List<ByteBuffer> node2way = new ArrayList<>();
	private List<ByteBuffer> nodeInterpolation = new ArrayList<>();
	
	private Map<Long, String> interpolation2Street = new HashMap<>();
	
	
	private boolean indexFilled = false;
	private boolean orderedByway = false;
	private AddrPointHandler handler; 
	
	private GeometryFactory factory = new GeometryFactory();
	private boolean byRealtionOrdered = false;
	
	public AddrPointsBuilder (AddrPointHandler handler) {
		this.handler = handler;
	}
	
	@Override
	public void handle(final Relation rel) {
		if(!indexFilled) {
			if(hasAddr(rel.tags)) {
				indexRelation(rel);
			}
		}
		else {
			orderByRelation();
			orderByWay();
			
			int i = Collections.binarySearch(way2relation, null, new Comparator<ByteBuffer>(){
				@Override
				public int compare(ByteBuffer bb, ByteBuffer key) {
					return Long.compare(bb.getLong(8), rel.id);
				}
			});
			
			for(ByteBuffer bb : findAll(way2relation, i, rel.id, 8)) {
				final long way = bb.getLong(0);
				
				int p = Collections.binarySearch(node2way, null, new Comparator<ByteBuffer>(){
					@Override
					public int compare(ByteBuffer bb, ByteBuffer key) {
						return Long.compare(bb.getLong(8), way);
					}
				});
				
				for(ByteBuffer bb2 : findAll(node2way, p, way, 8)) {
					double lon = bb2.getDouble(8 + 8 + 2);
					double lat = bb2.getDouble(8 + 8 + 2 + 8);
					
					Point point = factory.createPoint(new Coordinate(lon, lat));
					
					JSONObject meta = new JSONObject();
					
					meta.put("id", rel.id);
					meta.put("type", "relation");
					handler.handleAddrPoint(rel.tags, point, meta);
				}
			}
		}
	}

	private void orderByRelation() {
		if(!this.byRealtionOrdered) {
			Collections.sort(way2relation, Builder.SECOND_LONG_FIELD_COMPARATOR);
			this.byRealtionOrdered = true;
		}
	}

	private void indexRelation(Relation rel) {
		for (RelationMember rm : rel.members) {
			if(rm.type == ReferenceType.WAY) {
				ByteBuffer bb = ByteBuffer.allocate(8 + 8);
				bb.putLong(rm.ref).putLong(rel.id);
				way2relation.add(bb);
				
				//one way (one point) will be enough 
				break;
			}
		}
	}

	@Override
	public void firstRunDoneRelations() {
		Collections.sort(way2relation, Builder.FIRST_LONG_FIELD_COMPARATOR);
	}
	
	@Override
	public void handle(final Way line) {
		if(!indexFilled) {
			indexWay(line);
		}
		else {
			orderByWay();
			if(line.isClosed() && hasAddr(line.tags)) {
				buildAddrPointForWay(line);
			}
			else if (isInterpolation(line.tags)) {
				buildAddrPoints4Interpolation(line);
			}
		}
	}
	
	
	private void buildAddrPoints4Interpolation(final Way line) {
		String interpolation = line.tags.get(ADDR_INTERPOLATION);
		int step = getInterpolationStep(interpolation);
		
		if(step > 0) {
			List<ByteBuffer> points = getWayPoints(line);

			long prevPID = -1;
			short prevHN = -1;

			List<double[]> coords = new ArrayList<>();
			
			if(points.size() > 1) {
				for(ByteBuffer bb : points) {
					
					long pid = bb.getLong(0);
					double lon = bb.getDouble(8 + 8 + 2);
					double lat = bb.getDouble(8 + 8 + 2 + 8);
					
					if(pid != prevPID) {
						short hn = getInterpolationPointHN(pid);
						
						coords.add(new double[]{lon, lat});
						
						if(hn > 0 && coords.size() > 1) {
							interpolateSegment(step, prevHN, hn, coords, line, pid, prevPID);
							coords.clear();
							coords.add(new double[]{lon, lat});
						}
						
						if (hn <= 0 && line.nodes.get(0).equals(pid)) {
							System.err.println("Broken interpolation at point " + pid + " First point has no recognizeable addr:housenumber");
						}
						
						prevPID = pid;
						prevHN = hn > 0 ? hn : prevHN;
					}
				}
				
				if(coords.size() > 1) {
					System.err.println("Broken interpolation at point " + prevPID + " Last point has no recognizeable addr:housenumber");
				}
			}
		}
		else {
			System.err.println("Unsupported interpolation type: " + interpolation);
		}
		
	}

	private void interpolateSegment(int s, short prevHN, short hn,
			List<double[]> coords, Way way, long pid, long prevPID) {
		
		int from = Math.min(prevHN, hn);
		int to = Math.max(prevHN, hn);
		int step = Math.abs(s);
		
		int a = 0;
		Coordinate[] coordinates = new Coordinate[coords.size()];
		for(double[] d : coords) {
			coordinates[a++] = new Coordinate(d[0], d[1]);
		}
		LineString ls = factory.createLineString(coordinates);
		double length = ls.getLength();
		int steps = (to - from) / step;
		double dl = length / steps;
		
		for(int i = from, stepN = 0; i <= to; i += step, stepN++) {

				double l = stepN * dl;
				Coordinate c = new LocatePoint(ls, l).getPoint();
				
				JSONObject meta = new JSONObject();
				
				meta.put("id", way.id);
				meta.put("type", "interpolation");
				
				//such points will be duplicated by simple nodes, so mark it.
				if(i == from) {
					meta.put("firstInInterpolation", true);
					meta.put("basePointid", pid);
				}
				if(i == to) {
					meta.put("lastInInterpolation", true);
					meta.put("basePointid", prevPID);
				}
				
				if(way.tags.get(ADDR_STREET) == null && interpolation2Street.get(way.id) != null){
					way.tags.put(ADDR_STREET, interpolation2Street.get(way.id));
				}
				
				way.tags.put(ADDR_HOUSENUMBER, String.valueOf(i));
				
				handler.handleAddrPoint(way.tags, factory.createPoint(c), meta);
		}
		
	}

	private short getInterpolationPointHN(final long id) {
		int i = Collections.binarySearch(nodeInterpolation, null, new Comparator<ByteBuffer>() {
			@Override
			public int compare(ByteBuffer row, ByteBuffer key) {
				return Long.compare(row.getLong(0), id);
			}
		});

		if(i >= 0) {
			return nodeInterpolation.get(i).getShort(8);
		}
		
		return -1;
	}

	private int getInterpolationStep(String interpolation) {

		if("all".equalsIgnoreCase(interpolation)) {
			return 1;
		}

		if("even".equalsIgnoreCase(interpolation) || "odd".equalsIgnoreCase(interpolation)) {
			return 2;
		}
		
		try {
			return Integer.parseInt(interpolation);
		}
		catch (Exception e) {
			
		}
		
		return -1;
	}

	private List<ByteBuffer> getWayPoints(final Way line) {
		int i = Collections.binarySearch(node2way, null, new Comparator<ByteBuffer>() {
			@Override
			public int compare(ByteBuffer row, ByteBuffer key) {
				return Long.compare(row.getLong(8), line.id);
			}
		});

		List<ByteBuffer> points = findAll(node2way, i, line.id, 8);
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
			ByteBuffer bb = node2way.get(i);
			double lon = bb.getDouble(8 + 8 + 2);
			double lat = bb.getDouble(8 + 8 + 2 + 8);
			
			JSONObject meta = new JSONObject();
			
			meta.put("id", line.id);
			meta.put("type", "way");
			handler.handleAddrPoint(line.tags, factory.createPoint(new Coordinate(lon, lat)), meta);
		}
	}

	private void orderByWay() {
		if(!this.orderedByway) {
			Collections.sort(node2way, Builder.SECOND_LONG_FIELD_COMPARATOR);
			this.orderedByway = true;
		}
	}

	private void indexWay(Way line) {
		if(line.isClosed() && hasAddr(line.tags)) {
			ByteBuffer bb = ByteBuffer.allocate(8 + 8 + 2 + 8 + 8);
			bb.putLong(line.nodes.get(0)).putLong(line.id).putShort((short)0);
			node2way.add(bb);
		}
		else if (isInterpolation(line.tags)) {
			short i = 1;
			for(long p : line.nodes) {
				ByteBuffer bb = ByteBuffer.allocate(8 + 8 + 2 + 8 + 8);
				bb.putLong(p).putLong(line.id).putShort(i++);
				node2way.add(bb);
				
				bb = ByteBuffer.allocate(8 + 2 + 8);
				bb.putLong(0, p);
				bb.putLong(8 + 2, line.id);
				nodeInterpolation.add(bb);
			}
		}
		else if (findRelMemberIndex(line.id) >= 0) {
			ByteBuffer bb = ByteBuffer.allocate(8 + 8 + 2 + 8 + 8);
			bb.putLong(line.nodes.get(0)).putLong(line.id).putShort((short)0);
			node2way.add(bb);
		}
	}

	@Override
	public void firstRunDoneWays() {
		Collections.sort(node2way, Builder.FIRST_LONG_FIELD_COMPARATOR);
		Collections.sort(nodeInterpolation, Builder.FIRST_LONG_FIELD_COMPARATOR);
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
		
		if(hasAddr(node.tags)) {
			JSONObject meta = new JSONObject();
			
			meta.put("id", node.id);
			meta.put("type", "node");
			handler.handleAddrPoint(node.tags, factory.createPoint(new Coordinate(node.lon, node.lat)), meta);
		}
		
		indexNode2Way(node);
		
		indexNodeInterpolation(node);
		
	}

	private void indexNodeInterpolation(final Node node) {
		if(hasAddr(node.tags)) {
			int ni = Collections.binarySearch(nodeInterpolation, null, new Comparator<ByteBuffer>() {
				
				@Override
				public int compare(ByteBuffer row, ByteBuffer key) {
					return Long.compare(row.getLong(0), node.id);
				}
			});
			
			for(ByteBuffer bb : findAll(nodeInterpolation, ni, node.id, 0)) {
				bb.putShort(8, getHN(node.tags));
				
				String street = node.tags.get(ADDR_STREET);
				if(street != null) {
					long intWayId = bb.getLong(8 + 2);
					if(interpolation2Street.get(intWayId) == null) {
						interpolation2Street.put(intWayId, street);
					}
					else if(!interpolation2Street.get(intWayId).equals(street)) {
						System.err.println("Different streets on addr interpolated nodes. Interpolation way id: "
								+ intWayId + " street: " + street + " (" + interpolation2Street.get(intWayId) + ") Node: " + node.id);
					}
				}
			}
		}
		
	}

	private short getHN(Map<String, String> tags) {
		try {
			String hn = tags.get(ADDR_HOUSENUMBER);
			if(StringUtils.isNumericSpace(hn)) {
				return Short.valueOf(StringUtils.trim(hn));
			}
			else if(StringUtils.isNumericSpace(hn.substring(0, hn.length() - 2))) {
				return Short.valueOf(hn.substring(0, hn.length() - 2));
			}
		}
		catch (Exception e) {
		}
		return -1;
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
	
	private static boolean isInterpolation(Map<String, String> tags) {
		return tags.containsKey(ADDR_INTERPOLATION);
	}

	private static boolean hasAddr(Map<String, String> tags) {
		return tags.containsKey(ADDR_HOUSENUMBER);
	}

	@Override
	public void beforeLastRun() {
		this.indexFilled = true;
	}
	
}
