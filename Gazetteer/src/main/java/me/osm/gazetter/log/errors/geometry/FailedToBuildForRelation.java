package me.osm.gazetter.log.errors.geometry;

import me.osm.gazetter.log.LogLevel;
import me.osm.gazetter.log.LogLevel.Level;

import org.slf4j.Logger;

public class FailedToBuildForRelation extends FailedToBuildGeometry {

	private static final long serialVersionUID = 4942453656987600043L;
	
	private long relationId;

	public FailedToBuildForRelation(long relationId) {
		super(relationId, OSMType.RELATION);
	}
	
	@Override
	public void log(Logger root, Level level) {
		LogLevel.log(root, level, "Failed to build geometry for relation {}. No points found.", 
				this.relationId);
	}

}
