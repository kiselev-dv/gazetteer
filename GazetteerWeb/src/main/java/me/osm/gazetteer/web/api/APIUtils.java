package me.osm.gazetteer.web.api;

import javax.servlet.http.HttpServletRequest;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.json.JSONArray;
import org.json.JSONObject;

public class APIUtils {
	
	public static JSONObject encodeSearchResult(SearchResponse searchResponse, 
			boolean fullGeometry, boolean explain) {
		
		JSONObject result = new JSONObject();
		result.put("result", "success");
		
		JSONArray features = new JSONArray();
		result.put("features", features);
		
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
	
	public static void applyPaging(HttpServletRequest request,
			SearchRequestBuilder searchQ) {
		int pageSize = 20;
		if(request.getParameter("pagesize") != null) {
			pageSize = Integer.parseInt(request.getParameter("pagesize"));
		}
		
		int page = 1;
		if(request.getParameter("page") != null) {
			page = Integer.parseInt(request.getParameter("page"));
			if(page < 1) {
				page = 1;
			}
		}
		searchQ.setSize(pageSize);
		searchQ.setFrom((page - 1) * pageSize);
	}
	
}
