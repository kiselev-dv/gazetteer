package me.osm.gazetteer.psqlsearch.server.postprocessor;

import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.pipeline.Postprocessor;

public class AllowOriginPP implements Postprocessor {

	@Override
	public void process(Request request, Response response) {
		response.addHeader("Access-Control-Allow-Origin", "*");
	}

}
