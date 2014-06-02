package me.osm.gazetteer.web.api;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.json.JSONArray;
import org.json.JSONObject;
import org.restexpress.Request;

public class APIUtils {
	
	private static final String PAGE_PARAM = "page";
	private static final String PAGE_SIZE = "size";

	public static JSONObject encodeSearchResult(SearchResponse searchResponse, 
			boolean fullGeometry, boolean explain) {
		
		JSONObject result = new JSONObject();
		result.put("result", "success");
		
		JSONArray features = new JSONArray();
		result.put("features", features);
		
		result.put("hits", searchResponse.getHits().getTotalHits());
		
		for(SearchHit hit : searchResponse.getHits().getHits()) {
			JSONObject feature = new JSONObject(hit.getSource());
			
			if(!fullGeometry) {
				feature.remove("full_geometry");
			}
			
			features.put(feature);
		}
		
		if(explain) {
			JSONArray explanations = new JSONArray();
			result.put("explanations", explanations);

			for(SearchHit hit : searchResponse.getHits().getHits()) {
				explanations.put(hit.explanation().toHtml());
			}
		}
		
		return result;
	}
	
	public static void applyPaging(Request request,
			SearchRequestBuilder searchQ) {
		int pageSize = 20;
		if(request.getHeader(PAGE_SIZE) != null) {
			pageSize = Integer.parseInt(request.getHeader(PAGE_SIZE));
		}
		
		int page = 1;
		if(request.getHeader(PAGE_PARAM) != null) {
			page = Integer.parseInt(request.getHeader(PAGE_PARAM));
			if(page < 1) {
				page = 1;
			}
		}
		searchQ.setSize(pageSize);
		searchQ.setFrom((page - 1) * pageSize);
	}

	public static void resultPaging(Request request, JSONObject answer) {
		int pageSize = 20;
		if(request.getHeader(PAGE_SIZE) != null) {
			pageSize = Integer.parseInt(request.getHeader(PAGE_SIZE));
		}
		
		int page = 1;
		if(request.getHeader(PAGE_PARAM) != null) {
			page = Integer.parseInt(request.getHeader(PAGE_PARAM));
			if(page < 1) {
				page = 1;
			}
		}
		answer.put(PAGE_SIZE, pageSize);
		answer.put("from", (page - 1) * pageSize);
		answer.put(PAGE_PARAM, page);
	}
	
}
