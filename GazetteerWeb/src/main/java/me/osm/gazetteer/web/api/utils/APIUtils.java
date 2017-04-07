package me.osm.gazetteer.web.api.utils;

import me.osm.gazetteer.web.api.AnswerDetalization;

import org.apache.commons.lang3.StringEscapeUtils;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class APIUtils {
	
	public static JSONObject encodeSearchResult(SearchResponse searchResponse, 
			boolean fullGeometry, boolean explain, AnswerDetalization detalization) {
		
		JSONObject result = new JSONObject();
		result.put("result", "success");
		
		JSONArray features = new JSONArray();
		result.put("features", features);
		
		result.put("hits", searchResponse.getHits().getTotalHits());
		
		for(SearchHit hit : searchResponse.getHits().getHits()) {
			JSONObject feature = new JSONObject(hit.getSource());

			if(detalization == AnswerDetalization.SHORT) {
				JSONObject source = feature;
				feature = new JSONObject();
				
				feature.put("id", source.getString("id"));
				feature.put("center_point", source.getJSONObject("center_point"));
				feature.put("address", getAddressText(source));
			}
			
			if(detalization == AnswerDetalization.SHORT_SCORE) {
				JSONObject source = feature;
				feature = new JSONObject();
				
				feature.put("id", source.getString("id"));
				feature.put("center_point", source.getJSONObject("center_point"));
				feature.put("address", getAddressText(source));
				feature.put("addr_level", source.getString("addr_level"));
			}
			
			if(!fullGeometry) {
				feature.remove("full_geometry");
			}
			
			if(detalization != AnswerDetalization.SHORT) { 
				feature.put("_hit_score", hit.getScore());
			}
			
			features.put(feature);
		}
		
		if(explain) {
			JSONArray explanations = new JSONArray();
			result.put("explanations", explanations);

			for(SearchHit hit : searchResponse.getHits().getHits()) {
				explanations.put(StringEscapeUtils.escapeHtml4(hit.explanation().toString()));
			}
		}
		
		return result;
	}

	private static String getAddressText(JSONObject source) {
		try {
			return source.getJSONObject("address").getString("text");
		}
		catch (JSONException e) {
			return null;
		}
	}
	
}
