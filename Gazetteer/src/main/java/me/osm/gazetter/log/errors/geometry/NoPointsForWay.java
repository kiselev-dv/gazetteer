package me.osm.gazetter.log.errors.geometry;

import me.osm.gazetter.log.LogLevel;
import me.osm.gazetter.log.LogLevel.Level;

import org.slf4j.Logger;

public class NoPointsForWay extends FailedToBuildGeometry {

	private static final long serialVersionUID = 5999577412670660388L;
	
	public NoPointsForWay(long id) {
		super(id, OSMType.WAY);
	}
	
	@Override
	public void log(Logger root, Level level) {
		LogLevel.log(root, level, 
				"Failed to build geometry for way {}. No points found.", id);
	}

}
