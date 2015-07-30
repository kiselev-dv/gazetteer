package me.osm.gazetter.log.messages;

import me.osm.gazetter.log.LogLevel;
import me.osm.gazetter.log.LogLevel.Level;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.slf4j.Logger;

public class TimeMeasureMessage extends InfoMessage {

	private long ms;
	
	public TimeMeasureMessage(String message, long milliseconds) {
		super(message);
		ms = milliseconds;
	}
	
	@Override
	public void log(Logger root, Level level) {
		LogLevel.log(root, level, message, DurationFormatUtils.formatDurationHMS(ms));
	}

	private static final long serialVersionUID = 8324386185290888612L;

}
