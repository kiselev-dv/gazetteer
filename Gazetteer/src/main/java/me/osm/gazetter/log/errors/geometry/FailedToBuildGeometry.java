package me.osm.gazetter.log.errors.geometry;

import me.osm.gazetter.log.GazetteerLogMessage;

public abstract class FailedToBuildGeometry extends GazetteerLogMessage {

	private static final long serialVersionUID = 865856611237064681L;
	
	protected long id;
	
	protected OSMType type;

	public FailedToBuildGeometry(long id, OSMType type) {
		this.id = id;
		this.type = type;
	}
}
