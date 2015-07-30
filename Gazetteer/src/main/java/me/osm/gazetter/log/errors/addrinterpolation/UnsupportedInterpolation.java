package me.osm.gazetter.log.errors.addrinterpolation;

import org.slf4j.Logger;

import me.osm.gazetter.log.GazetteerLogMessage;
import me.osm.gazetter.log.LogLevel;
import me.osm.gazetter.log.LogLevel.Level;

public class UnsupportedInterpolation extends GazetteerLogMessage {

	private static final long serialVersionUID = 5711486411805428467L;
	
	private String type;
	private long line;

	public UnsupportedInterpolation(String interpolation, long id) {
		this.type = interpolation;
		this.line = id;
	}

	@Override
	public void log(Logger root, Level level) {
		LogLevel.log(root, level, 
				"Unsupported interpolation type: {} for line {}", type, line);
	}

}
