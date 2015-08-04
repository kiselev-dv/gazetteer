package me.osm.gazetter.striper.builders;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import me.osm.gazetter.striper.readers.RelationsReader.Relation;
import me.osm.gazetter.striper.readers.WaysReader.Way;
import me.osm.gazetter.utils.MultiMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

public class BuildUtils {
	
	private static final Logger log = LoggerFactory.getLogger(BoundariesBuilder.class.getName());
	private static final  GeometryFactory geometryFactory = new GeometryFactory();
	
	public static MultiPolygon buildMultyPolygon(final Relation rel,
			List<LineString> outers, List<LineString> inners) {
		
		MultiPolygon outer = polygonizeLinestrings(rel, outers);
		
		if(outer == null) {
			return null;
		}
		
		if(!outer.isValid()) {
			IsValidOp validOptions = new IsValidOp(outer);
			TopologyValidationError validationError = validOptions.getValidationError();
			
			if(validationError.getErrorType() == TopologyValidationError.SELF_INTERSECTION) {
				log.info("Trying to mend polygon for {}. Error is {}", rel.id, validationError.toString());

				try {
					
					// http://stackoverflow.com/questions/31473553/
					// is-there-a-way-to-convert-a-self-intersecting-polygon-to-a-multipolygon-in-jts
					Geometry mendedG = SelfIntersectionsMender.mend(outer);
					MultiPolygon mended = null;
					
					if(mendedG instanceof MultiPolygon) {
						mended = (MultiPolygon)mendedG;
					}
					else if(mendedG instanceof Polygon) {
						mended = geometryFactory.createMultiPolygon(
								new Polygon[]{(Polygon) mendedG });
					}
					
					if(mended == null || !mended.isValid() || mended.isEmpty()) {
						mended = dropOverlaps(outer, validOptions);
					}

					if(mended == null || !mended.isValid() || mended.isEmpty()) {
						log.warn("Can't mend polygon for {}.", rel.id);
						if(log.isDebugEnabled()) {
							log.debug(outer.toString());
						}
						
						return null;
					}
					
					outer = mended;
				}
				catch (Exception e) {
					log.warn("Failed to mend polygon for {}. Cause: {}", rel.id, e.getMessage());
				}
				
			}
			else {
				log.warn("Polygon for relation {} is invalid. Error is {}", rel.id, validationError.toString());
				if(log.isDebugEnabled()) {
					log.debug(outer.toString());
				}
				return null;
			}
		}
		
		if(inners == null || inners.isEmpty()) {
			return outer;
		}
		
		MultiPolygon inner = polygonizeLinestrings(rel, inners);
		
		MultiPolygon mix = substract(outer, inner);
		
		if(mix != null && !mix.isEmpty()) {
			return mix;
		}
		else {
			log.warn("Multipolygon is invalid after substract inners. Use only outers. Rel. id: {}", rel.id);
			return outer;
		}
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

	private static MultiPolygon polygonizeLinestrings(final Relation rel, List<LineString> linestrings) {
		if(!linestrings.isEmpty()) {
			
			MultiMap<Point, LineString> point2line = new MultiMap<Point, LineString>(); 
			
			for(LineString ls : linestrings) {
				point2line.put(ls.getStartPoint(), ls);
				point2line.put(ls.getEndPoint(), ls);
			}
			
			boolean closed = true;
			for(Entry<Point, Set<LineString>> entry : point2line.entrySet()) {
				if(entry.getValue().size() < 2 ) {
					if(!(entry.getValue().size() == 1 && entry.getValue().iterator().next().isClosed())) {
						log.warn("Not closed ring in multipolygon. Relation {} near {}", 
								rel.id, entry.getKey().toString());
						
						closed = false;
					}
				}
			}
			if(!closed) {
				if(log.isDebugEnabled()) {
					for(LineString ls : linestrings) {
						log.debug("Not closed ring in {}. Way: {}", rel.id, ls.toString());
					}
				}
				return null;
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
			}
			catch (Exception e) {
				if(log.isWarnEnabled()) {

					StringBuilder sb = new StringBuilder();
					WKTWriter wktWriter = new WKTWriter();
					for(LineString ls : linestrings) {
						sb.append("\n").append(wktWriter.write(ls));
					}
					
					log.warn("Cant polygonize relation: {} \nLines ({}):{}\nCause: {}", new Object[]{
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
		int c = 0;
		if(!nodes.isEmpty()) {
			Coordinate[] geometry = new Coordinate[line.nodes.size()];
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
					
					geometry[c++] = new Coordinate(lon, lat);
				}
			}
			return geometry;
		}
		
		return null;
	}
}
