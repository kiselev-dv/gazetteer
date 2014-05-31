package me.osm.gazetteer.web.api;

import me.osm.gazetteer.web.imp.Importer;

import org.restexpress.Request;
import org.restexpress.Response;

public class ImportAPI {
	
	public void read(Request request, Response response){
		String source = request.getHeader("source", "Source not set.");
		new Importer(source).run();
		response.setResponseNoContent();
	}
	
}
