package me.osm.gazetter.log.errors.geometry;

import me.osm.gazetter.log.LogLevel;
import me.osm.gazetter.log.LogLevel.Level;

import org.slf4j.Logger;

public class WrongNumberOfPoints extends FailedToBuildGeometry {

	private static final long serialVersionUID = 2409853424825025687L;
	
	public WrongNumberOfPoints(long id) {
		super(id, OSMType.WAY);
	}

	@Override
	public void log(Logger root, Level level) {
		LogLevel.log(root, level, "Wrong number of points for {}", id);
	}

}
