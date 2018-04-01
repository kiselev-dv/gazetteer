package me.osm.gazetteer.psqlsearch.server;

import java.io.Serializable;

import org.apache.commons.lang3.StringUtils;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.pipeline.MessageObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.helpers.BasicMarkerFactory;

public final class HttpLogger extends MessageObserver {
	
	private static final BasicMarkerFactory BASIC_MARKER_FACTORY = new BasicMarkerFactory();
	
	private static final Logger logger = LoggerFactory.getLogger(HttpLogger.class);
	private static final Logger accLog = LoggerFactory.getLogger(HttpLogger.class.getName() + ".AccessLog");
	
	private static final Marker HUMAN = BASIC_MARKER_FACTORY.getMarker("HUMAN"); 
	
	private static final Marker BOT_GOOGLE = BASIC_MARKER_FACTORY.getMarker("BOT.GOOGLE"); 
	private static final Marker BOT_YANDEX = BASIC_MARKER_FACTORY.getMarker("BOT.YANDEX"); 
	private static final Marker BOT_BING = BASIC_MARKER_FACTORY.getMarker("BOT.BING"); 
	
	@Override
	protected void onException(Throwable exception, Request request,
			Response response) {
		
		logger.error("{} {} threw exception: {}", 
				request.getEffectiveHttpMethod(), 
				request.getUrl(), 
				exception.getMessage(), exception);
	}
	
	@Override
	protected void onComplete(Request request, Response response) {
		
		String realIp = request.getHeader("X-Real-IP");
		Serializable ipAddr = realIp == null ? request.getRemoteAddress().getAddress() : realIp;
		String userAgent = request.getHeader("User-Agent");
		
		Marker marker = HUMAN;
		if(StringUtils.contains(userAgent, "Googlebot")) {
			marker = BOT_GOOGLE;
		}
		
		else if(StringUtils.contains(userAgent, "YandexBot")) {
			marker = BOT_YANDEX;
		}

		else if(StringUtils.contains(userAgent, "msnbot") 
				|| StringUtils.contains(userAgent, "BingPreview")
				|| StringUtils.contains(userAgent, "bingbot")) {
			marker = BOT_BING;
		}
		
		accLog.trace(marker, "{} - {} {} {} User-Agent: {} ",
				ipAddr,
				response.getResponseStatus().code(),
				request.getEffectiveHttpMethod(), 
				request.getUrl(), 
				userAgent);
		
		if(response.getResponseStatus().code() != 200) {
			logger.warn("{} {} responded with {}", new Object[]{
					request.getEffectiveHttpMethod().toString(), 
					request.getUrl(),
					response.getResponseStatus().toString()});
		}
			
	}
}