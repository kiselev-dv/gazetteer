package me.osm.gazetter.striper.builders;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.osm.gazetter.striper.readers.RelationsReader.Relation;
import me.osm.gazetter.striper.readers.WaysReader.Way;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.io.WKTWriter;
import com.vividsolutions.jts.operation.polygonize.Polygonizer;

public class BuildUtils {
	
	private static final Logger log = LoggerFactory.getLogger(BoundariesBuilder.class.getName());
	private static final  GeometryFactory geometryFactory = new GeometryFactory();
	
	public static MultiPolygon buildMultyPolygon(final Relation rel,
			List<LineString> lines) {
		if(!lines.isEmpty()) {
			Polygonizer polygonizer = new Polygonizer();
			polygonizer.add(lines);
			
			try	{
				@SuppressWarnings("unchecked")
				Collection<Polygon> polygons = polygonizer.getPolygons();
				if(!polygons.isEmpty()) {
					Polygon[] ps =  polygons.toArray(new Polygon[polygons.size()]);
					MultiPolygon mp = geometryFactory.createMultiPolygon(ps);
					if(mp.isValid())
						return mp;
				}
			}
			catch (Exception e) {
				if(log.isWarnEnabled()) {

					StringBuilder sb = new StringBuilder();
					WKTWriter wktWriter = new WKTWriter();
					for(LineString ls : lines) {
						sb.append("\n").append(wktWriter.write(ls));
					}
					
					log.warn("Cant polygonize relation: {} \nLines ({}):{}\nCause: {}", new Object[]{
							rel.id,
							lines.size(),
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
