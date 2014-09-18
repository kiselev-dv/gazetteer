package me.osm.gazetter.striper.builders;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import me.osm.gazetter.striper.readers.RelationsReader.Relation;
import me.osm.gazetter.striper.readers.WaysReader.Way;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiPolygon;
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
		
		MultiPolygon outer = buildPolygon(rel, outers);
		if(inners == null || inners.isEmpty()) {
			return outer;
		}
		
		MultiPolygon inner = buildPolygon(rel, inners);
		
		MultiPolygon mix = substract(outer, inner);
		
		if(mix != null) {
			return mix;
		}
		else {
			log.warn("Multipolygon is invalid after substract inners. Use only outers. Rel. id: {}", rel.id);
			return outer;
		}
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

	private static MultiPolygon buildPolygon(final Relation rel, List<LineString> linestrings) {
		if(!linestrings.isEmpty()) {
			Polygonizer polygonizer = new Polygonizer();
			polygonizer.add(linestrings);
			
			try	{
				@SuppressWarnings("unchecked")
				Collection<Polygon> polygons = polygonizer.getPolygons();
				if(!polygons.isEmpty()) {
					Polygon[] ps =  polygons.toArray(new Polygon[polygons.size()]);
					MultiPolygon mp = geometryFactory.createMultiPolygon(ps);
					if(mp.isValid()) {
						return mp;
					}
					else {
						IsValidOp validOptions = new IsValidOp(mp);
						TopologyValidationError validationError = validOptions.getValidationError();

						log.warn("Polygon for relation {} is invalid. Error is {}", rel.id, validationError.toString());
						if(log.isDebugEnabled()) {
							log.debug(mp.toString());
						}
					}
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
