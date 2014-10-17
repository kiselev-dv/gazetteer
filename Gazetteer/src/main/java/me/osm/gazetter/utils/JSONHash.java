package me.osm.gazetter.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

public class JSONHash {

	public static String asCanonicalString(Object obj, Set<String> ignoreKeys) {
		
		StringBuilder sb = new StringBuilder();
		
		traverse(obj, sb, ignoreKeys);
		
		return sb.toString();
	}

	private static void traverse(Object obj, StringBuilder sb,
			Set<String> ignoreKeys) {
		
		if (obj instanceof JSONObject) {
			JSONObject object = (JSONObject) obj;
			
			List<String> keys = new ArrayList<String>();
			keys.addAll(object.keySet());
			Collections.sort(keys);
			
			sb.append("{");
			for(String key : keys) {
				if(!ignoreKeys.contains(key)) {
					sb.append("\"").append(key).append("\":{")
						.append(asCanonicalString(object.get(key), ignoreKeys)).append("}");
				}
			}
			sb.append("}");
		}
		else if (obj instanceof JSONArray) {
			JSONArray array = (JSONArray) obj;
			List<String> elements = new ArrayList<String>();
			
			for(int i = 0; i < array.length(); i++) {
				elements.add(asCanonicalString(array.get(i), ignoreKeys));
			}
			
			Collections.sort(elements);
			sb.append("[");
			sb.append(StringUtils.join(elements, ","));
			sb.append("]");
		}
		else {
			sb.append(obj);
		}
		
	}

}
