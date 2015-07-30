package me.osm.gazetter.log.errors;

import org.slf4j.Logger;

import me.osm.gazetter.log.GazetteerLogMessage;
import me.osm.gazetter.log.LogLevel;
import me.osm.gazetter.log.LogLevel.Level;

public class StripeJoinFailed extends GazetteerLogMessage {

	private static final long serialVersionUID = 980228009193133075L;

	private String stripe;
	
	public StripeJoinFailed(String stripe) {
		this.stripe = stripe;
	}
	
	@Override
	public void log(Logger root, Level level) {
		LogLevel.log(root, level, "Failed to join stripe {}", stripe);
	}

}
