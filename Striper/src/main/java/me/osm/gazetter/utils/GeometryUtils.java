package me.osm.gazetter.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.geom.util.LineStringExtracter;
import com.vividsolutions.jts.operation.polygonize.Polygonizer;

public class GeometryUtils {
	
    public static List<Polygon> splitPolygon(Polygon polygon, LineString line)
    {
    	GeometryFactory f = new GeometryFactory();
    	
        Geometry nodedLinework = polygon.getBoundary().union(line);
        Polygonizer polygonizer = new Polygonizer();
        polygonizer.add(LineStringExtracter.getLines(nodedLinework));
        @SuppressWarnings("unchecked")
		Collection<Polygon> polygons = polygonizer.getPolygons();
        
        // only keep polygons which are inside the input
        List<Polygon> output = new ArrayList<>();
        for (Polygon p : polygons)
        {
            if (polygon.contains(p.getInteriorPoint()))
                output.add(p);
        }
        
        List<Polygon> result = new ArrayList<>();
        
        for(Polygon outer : output) {
        	
        	Polygon r = outer;
        	
        	for(int i = 0; i < polygon.getNumInteriorRing(); i++) {
        		LineString interiorRing = polygon.getInteriorRingN(i);
        		LinearRing interior = f.createLinearRing(interiorRing.getCoordinates());
        		Polygon inner = f.createPolygon(interior);
        		
        		if(inner.intersects(outer)) {
        			Polygon difference = (Polygon) outer.difference(inner);
        			if(difference.isValid()) {
        				r = difference;
        			}
        		}
        	}
        	
        	result.add(r);
        }

        return result;
    }
    
}
