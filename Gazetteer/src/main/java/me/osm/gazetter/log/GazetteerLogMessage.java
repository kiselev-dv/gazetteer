package me.osm.gazetter.log;

import java.io.Serializable;
import java.util.Date;

import me.osm.gazetter.log.LogLevel.Level;

import org.slf4j.Logger;

public abstract class GazetteerLogMessage implements Serializable {

	private static final long serialVersionUID = 3326041706268701939L;

	public abstract void log(Logger root, Level level);

	protected String cause;
	
	protected Date sendDate;
	
	public void setCause(Class<?> cause) {
		this.cause = cause.getName();
	}
	
	public void setSendDate(Date date) {
		this.sendDate = date;
	}

}
