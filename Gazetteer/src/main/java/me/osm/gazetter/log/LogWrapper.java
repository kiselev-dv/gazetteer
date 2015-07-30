package me.osm.gazetter.log;

import me.osm.gazetter.log.LogLevel.Level;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogWrapper {
	
	private Logger root;
	
	private static volatile LogWrapperSendListener sendListener;
	
	public static void setListener(LogWrapperSendListener listener) {
		sendListener = listener;
	}
	
	private Class<?> cause;
	
	public LogWrapper (Class<?> cause) {
		root = LoggerFactory.getLogger(cause);
		this.cause = cause;
	}

	public void trace(GazetteerLogMessage error) {
		error.log(root, Level.TRACE);
		send(error);
	}

	public void info(GazetteerLogMessage error) {
		error.log(root, Level.INFO);
		send(error);
	}
	
	public void warn(GazetteerLogMessage error) {
		error.log(root, Level.WARN);
		send(error);
	}
	
	public void debug(GazetteerLogMessage error) {
		error.log(root, Level.DEBUG);
		send(error);
	}

	public void error(GazetteerLogMessage error) {
		error.log(root, Level.ERROR);
		send(error);
	}

	private void send(GazetteerLogMessage error) {
		error.setCause(this.cause);
		sendListener.sendMsg(error);
	}
	
}
