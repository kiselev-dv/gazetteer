package me.osm.gazetteer.web.postprocessor;

import org.json.JSONObject;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.pipeline.Postprocessor;

public final class MarkHeaderPostprocessor implements Postprocessor {
	
	private static final String MARK_HEADER = "mark";

	@Override
	public void process(Request request, Response response) {
		Object body = response.getBody();
		
		if(body != null && body instanceof JSONObject) {
			if(request.getHeader(MARK_HEADER) != null) {
				((JSONObject)body).put("mark", request.getHeader(MARK_HEADER));
			}
		}
	}
}