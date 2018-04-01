package me.osm.gazetteer.psqlsearch.api;

import org.restexpress.Request;
import org.restexpress.Response;

public interface SearchAPI {
	
	public ResultsWrapper read(Request request, Response response);

}
