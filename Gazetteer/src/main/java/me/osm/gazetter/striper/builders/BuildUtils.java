package me.osm.gazetter.striper.builders;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import me.osm.gazetter.LOGMarkers;
import me.osm.gazetter.striper.readers.RelationsReader.Relation;
import me.osm.gazetter.striper.readers.RelationsReader.Relation.RelationMember;
import me.osm.gazetter.striper.readers.WaysReader.Way;
import me.osm.gazetter.utils.MultiMap;

import org.slf4j.Logger;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.io.WKTWriter;
import com.vividsolutions.jts.operation.polygonize.Polygonizer;
import com.vividsolutions.jts.operation.valid.IsValidOp;
import com.vividsolutions.jts.operation.valid.TopologyValidationError;

/**
 * Geometry creation utilities
 * 
 * Provides:
 * 		Polygonization
 *      Invalid geometry mending
 *      
 * */
public class BuildUtils {
	
	//private static final Logger log = LoggerFactory.getLogger(BuildUtils.class);
	private static final  GeometryFactory geometryFactory = new GeometryFactory();
	
	private static final SelfIntersectionsMender siMender = new BufferSelfIntersectionsMender();
	
	/**
	 * Build MultiPolygon geometry 
	 * 
	 * Create MultiPolygon for provided collections of
	 * inner and outer line segments.
	 * 
	 * Trying to mend errors such as Self-Intersections
	 * 
	 * */
	public static MultiPolygon buildMultyPolygon(final Logger log, final Relation rel,
			List<LineString> outers, List<LineString> inners) {
		
		MultiPolygon outer = buildOuterGeometry(log, rel, outers);
		
		if(outer == null) {
			return null;
		}
		
		if(inners == null || inners.isEmpty()) {
			return outer;
		}
		
		MultiPolygon inner = polygonizeLinestrings(log, rel, inners);
		
		MultiPolygon mix = substract(outer, inner);
		
		
		if(mix != null && !mix.isEmpty()) {
			
			IsValidOp validOptions = new IsValidOp(mix);
			if(!validOptions.isValid() && 
					validOptions.getValidationError().getErrorType() 
						== TopologyValidationError.DISCONNECTED_INTERIOR) {
				
				mix = (MultiPolygon) mix.buffer(0.0);
			}

			return mix;
		}
		else {
			log.warn("Multipolygon is invalid after substract inners. Use only outers. Rel. id: {}", rel.id);
			return outer;
		}
	}

	private static MultiPolygon buildOuterGeometry(final Logger log,
			final Relation rel, List<LineString> outers) {
		
		MultiPolygon outer = polygonizeLinestrings(log, rel, outers);
		
		if(outer == null) {
			return null;
		}
		
		IsValidOp validOptions = new IsValidOp(outer);
		if(!validOptions.isValid()) {
			TopologyValidationError validationError = validOptions.getValidationError();
			
			MultiPolygon mended = null;

			try {
				
				int errorType = validationError.getErrorType();
				
				if(errorType == TopologyValidationError.SELF_INTERSECTION 
						|| errorType == TopologyValidationError.RING_SELF_INTERSECTION ) {
	
					Geometry mendedG = siMender.mend(outer);
					
					if(mendedG instanceof MultiPolygon) {
						mended = (MultiPolygon)mendedG;
					}
					else if(mendedG instanceof Polygon) {
						mended = geometryFactory.createMultiPolygon(
								new Polygon[]{(Polygon) mendedG });
					}
					
				}
				else if (errorType == TopologyValidationError.NESTED_SHELLS) {
					mended = dropOverlaps(outer, validOptions);
				}
				else {
					log.warn("Polygon for relation {} is invalid. Error is {}", 
							rel.id, validationError.toString());
					if(log.isDebugEnabled()) {
						log.debug(outer.toString());
					}
					return null;
				}
				
				if(mended == null || !mended.isValid() || mended.isEmpty()) {
					log.warn("Can't mend polygon for {}.", rel.id);
					if(log.isDebugEnabled()) {
						log.debug(outer.toString());
					}
					return null;
				}
				
				log.info(LOGMarkers.E_POLYGON_BUID_ERR, 
						"Polygon for rel_osm_id({}) mended. Error is {}", 
						rel.id, validationError.toString());
				
				outer = mended;
			}
			catch (Exception e) {
				log.warn("Failed to mend polygon for {}. Cause: {}", rel.id, e.getMessage());
			}
		}
		
		return outer;
	}

	private static MultiPolygon dropOverlaps(MultiPolygon outer, IsValidOp validOptions) {
		
			Geometry result = null;
			for(int i=0; i < outer.getNumGeometries(); i++) {
				Geometry geometryN = outer.getGeometryN(i);
				
				if(i == 0) {
					result = geometryN;
				}
				else {
					result = result.union(geometryN);
				}
			}
			
			if(result instanceof MultiPolygon) {
				return (MultiPolygon) result;
			}
			
			if(result instanceof Polygon) {
				return geometryFactory.createMultiPolygon(new Polygon[]{(Polygon)result});
			}
		
		return null;
	}

	private static MultiPolygon substract(MultiPolygon outer, MultiPolygon inner) {
		
		List<Polygon> polygons = new ArrayList<Polygon>();
		
		if(inner != null && !inner.isEmpty()) {

			for(int j = 0; j < outer.getNumGeometries(); j++) {
				Polygon outerN = (Polygon) outer.getGeometryN(j);
				
				for(int i = 0; i < inner.getNumGeometries(); i++) {
				Polygon innerN = (Polygon) inner.getGeometryN(i);
					if(outerN.intersects(innerN)) {
						outerN = (Polygon) outerN.difference(innerN);
					}
				}
				
				if(!outerN.isEmpty()) {
					polygons.add(outerN);
				}
			}
		}
		
		Polygon[] ps =  polygons.toArray(new Polygon[polygons.size()]);
		MultiPolygon mp = geometryFactory.createMultiPolygon(ps);
		if(mp.isValid()) {
			return mp;
		}
		
		return null;
	}

	private static MultiPolygon polygonizeLinestrings(final Logger log, final Relation rel, List<LineString> linestrings) {
		if(!linestrings.isEmpty()) {
			
			MultiMap<Point, LineString> point2line = new MultiMap<Point, LineString>(); 
			
			for(LineString ls : linestrings) {
				point2line.put(ls.getStartPoint(), ls);
				point2line.put(ls.getEndPoint(), ls);
			}
			
			for(Entry<Point, Set<LineString>> entry : point2line.entrySet()) {
				if(entry.getValue().size() < 2 ) {
					if(!(entry.getValue().size() == 1 && entry.getValue().iterator().next().isClosed())) {
						log.warn("Not closed ring in multipolygon. Relation {} near {}", 
								rel.id, entry.getKey().toString());
					}
				}
			}
			
			Polygonizer polygonizer = new Polygonizer();
			polygonizer.add(linestrings);
			
			try	{
				@SuppressWarnings("unchecked")
				Collection<Polygon> polygons = polygonizer.getPolygons();
				if(!polygons.isEmpty()) {
					Polygon[] ps =  polygons.toArray(new Polygon[polygons.size()]);
					MultiPolygon mp = geometryFactory.createMultiPolygon(ps);
					return mp;
				}
				log.warn("Got empty polygon for relation {}", rel.id);
			}
			catch (Exception e) {
				if(log.isWarnEnabled()) {

					StringBuilder sb = new StringBuilder();
					WKTWriter wktWriter = new WKTWriter();
					for(LineString ls : linestrings) {
						sb.append("\n").append(wktWriter.write(ls));
					}
					
					log.warn(LOGMarkers.E_POLYGON_BUID_ERR, "Cant polygonize relation: rel_osm_id({}) \nLines ({}):{}\nCause: {}", new Object[]{
							rel.id,
							linestrings.size(),
							sb.toString(),
							e.getMessage()
					});
				}
			}
		}
		
		return null;
	}
	
	public static Coordinate[] buildWayGeometry(Way line, List<ByteBuffer> nodes, 
			final int idOffset, int lonOffset, int latOffset) {
		if(!nodes.isEmpty()) {
			List<Coordinate> geometry = new ArrayList<>(line.nodes.size());
			Collections.sort(nodes, Builder.FIRST_LONG_FIELD_COMPARATOR);
			
			for(final long n : line.nodes) {
				int ni = Collections.binarySearch(nodes, null, new Comparator<ByteBuffer>() {
					@Override
					public int compare(ByteBuffer bb, ByteBuffer key) {
						return Long.compare(bb.getLong(idOffset), n);
					}
				});
				
				if(ni >= 0) {
					ByteBuffer node = nodes.get(ni);
					double lon = node.getDouble(lonOffset);
					double lat = node.getDouble(latOffset);
					
					geometry.add(new Coordinate(lon, lat));
				}
			}
			return geometry.toArray(new Coordinate[geometry.size()]);
		}
		
		return null;
	}
}
