package me.osm.gazetter.log.errors.geometry;

import me.osm.gazetter.log.LogLevel;
import me.osm.gazetter.log.LogLevel.Level;

import org.slf4j.Logger;

public class SomePointsWasntFound extends FailedToBuildGeometry {

	private static final long serialVersionUID = -3836786564645113673L;

	public SomePointsWasntFound(long id) {
		super(id, OSMType.WAY);
	}
	
	@Override
	public void log(Logger root, Level level) {
		LogLevel.log(root, level, 
				"Failed to build geometry for way {}. Some points wasn't found.", id);
	}

}
