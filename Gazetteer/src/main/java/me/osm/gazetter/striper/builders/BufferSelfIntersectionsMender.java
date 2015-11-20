package me.osm.gazetter.striper.builders;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;

/**
 * Mends invalid polygons
 * */
public class BufferSelfIntersectionsMender implements SelfIntersectionsMender {

	/* (non-Javadoc)
	 * @see me.osm.gazetter.striper.builders.SelfIntersectionsMender#mend(com.vividsolutions.jts.geom.Geometry)
	 */
	@Override
	@SuppressWarnings("unchecked")
	public Geometry mend(Geometry geom){
	    if(geom instanceof Polygon || geom instanceof MultiPolygon) {
	    	return geom.buffer(0.0);
	    }else{
	    	// In my case, I only care about polygon / multipolygon geometries
	        return geom; 
	    }
	}

}
