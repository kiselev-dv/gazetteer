package me.osm.gazetteer.web.api;

import me.osm.gazetteer.web.imp.IndexHolder;

import org.restexpress.Request;
import org.restexpress.Response;

public class IndexAPI {
	
	
	public String read(Request request, Response response) {
		
		boolean rebuild = "true".equals(request.getHeader("rebuild"));
		
		if(rebuild) {
			IndexHolder.dropIndex();
			IndexHolder.createIndex();
			return "done";
		}
		
		return "nothing to do";
	}
}
