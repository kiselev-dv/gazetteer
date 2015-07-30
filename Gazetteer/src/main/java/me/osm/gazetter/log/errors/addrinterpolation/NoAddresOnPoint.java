package me.osm.gazetter.log.errors.addrinterpolation;

import org.slf4j.Logger;

import me.osm.gazetter.log.GazetteerLogMessage;
import me.osm.gazetter.log.LogLevel;
import me.osm.gazetter.log.LogLevel.Level;

public class NoAddresOnPoint extends GazetteerLogMessage {

	private static final long serialVersionUID = 5848727288966259101L;
	
	private long pointId;
	
	public NoAddresOnPoint(long pointId) {
		this.pointId = pointId;
	}

	@Override
	public void log(Logger root, Level level) {
		LogLevel.log(root, level, "Broken interpolation at point {}. "
				+ "Point has no recognizeable addr:housenumber", 
				this.pointId);
	}

}
