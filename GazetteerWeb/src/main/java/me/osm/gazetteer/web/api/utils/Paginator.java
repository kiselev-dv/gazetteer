package me.osm.gazetteer.web.api.utils;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.json.JSONObject;
import org.restexpress.Request;

public interface Paginator {

	/**
	 * Setup paging for ElasticSearch query
	 * */
	public void patchSearchQ(Request request, SearchRequestBuilder searchQ);

	/**
	 * Add page info into result
	 * */
	public void patchAnswer(Request request, JSONObject answer);

}