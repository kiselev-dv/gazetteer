package me.osm.gazetter.log.messages;

import org.slf4j.Logger;

import me.osm.gazetter.log.GazetteerLogMessage;
import me.osm.gazetter.log.LogLevel;
import me.osm.gazetter.log.LogLevel.Level;

public class InfoMessage extends GazetteerLogMessage {

	private static final long serialVersionUID = 3349275670886083433L;
	
	protected String message;

	public InfoMessage(String message) {
		this.message = message;
	}
	
	@Override
	public void log(Logger root, Level level) {
		LogLevel.log(root, level, message);
	}

}
