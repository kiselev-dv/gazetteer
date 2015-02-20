package me.osm.gazetteer.web;

import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.pipeline.MessageObserver;

public final class HttpLogger extends MessageObserver {
	@Override
	protected void onException(Throwable exception, Request request,
			Response response) {
		
		System.out.println(request.getEffectiveHttpMethod().toString() 
				+ " " 
				+ request.getUrl()
				+ " threw exception: "
				+ exception.getClass().getSimpleName());
			exception.printStackTrace();
	}

	@Override
	protected void onComplete(Request request, Response response) {
		
		if(response.getResponseStatus().getCode() != 200) {
			
			StringBuffer sb = new StringBuffer(request.getEffectiveHttpMethod().toString());
			sb.append(" ");
			sb.append(request.getUrl());
			
			sb.append(" responded with ");
			sb.append(response.getResponseStatus().toString());
			
			System.out.println(sb.toString());
		}
	}
}