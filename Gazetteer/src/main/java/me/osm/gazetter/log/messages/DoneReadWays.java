package me.osm.gazetter.log.messages;

import me.osm.gazetter.log.GazetteerLogMessage;
import me.osm.gazetter.log.LogLevel;
import me.osm.gazetter.log.LogLevel.Level;

import org.slf4j.Logger;

public final class DoneReadWays extends GazetteerLogMessage {
	
	private static final long serialVersionUID = -9027294516852559553L;
	
	private int counter;
	
	public DoneReadWays(int counter) {
		this.counter = counter;
	}
	
	@Override
	public void log(Logger root, Level level) {
		LogLevel.log(root, level, "Done read ways. {} nodes added to index.", counter);
	}
}