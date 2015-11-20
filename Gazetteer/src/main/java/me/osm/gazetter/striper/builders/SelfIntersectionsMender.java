package me.osm.gazetter.striper.builders;

import com.vividsolutions.jts.geom.Geometry;

public interface SelfIntersectionsMender {

	/**
	 * Get / create a valid version of the geometry given. 
	 * 
	 * If the geometry is a polygon or multipolygon, self intersections /
	 * inconsistencies are fixed. Otherwise the geometry is returned.
	 * 
	 * @param geom
	 * @return a geometry 
	 */
	public Geometry mend(Geometry geom);

}