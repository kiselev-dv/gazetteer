package me.osm.gazetteer.web.utils;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

public class VelocityHelper {
	
	public static final VelocityHelper INSTANCE = new VelocityHelper();
	
	private VelocityHelper() {
		
	}
	
	public List<JSONObject> objList(JSONObject subj, String key) {
		List<JSONObject> result = new ArrayList<JSONObject>();

		JSONArray jsonArray = subj.optJSONArray(key);
		if(jsonArray != null) {
			for(int i = 0; i < jsonArray.length(); i++) {
				result.add(jsonArray.getJSONObject(i));
			}
		}
		
		return result;
	}

	public List<String> stringList(JSONObject subj, String key) {
		List<String> result = new ArrayList<String>();
		
		JSONArray jsonArray = subj.optJSONArray(key);
		if(jsonArray != null) {
			for(int i = 0; i < jsonArray.length(); i++) {
				result.add(jsonArray.getString(i));
			}
		}
		
		return result;
	}

	public String link(String id, String t) {
		return StringUtils.replace(t, "{id}", id);
	}
	
	public String esc(String t) {
		return StringEscapeUtils.escapeXml(t);
	}
}
