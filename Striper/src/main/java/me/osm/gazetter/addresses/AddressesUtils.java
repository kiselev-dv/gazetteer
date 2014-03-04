package me.osm.gazetter.addresses;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONObject;

public class AddressesUtils {
	
	private static final Set<String> PLACES = new HashSet<>(Arrays.asList(new String[]{
			"city",
			"town",
			"hamlet",
			"village",
			"isolated_dwelling",
			"locality",
			"allotments",
			"suburb",
			"neighbourhood",
			"quarter",
	}));
	
//	public static List<JSONObject> filterPlaces(List<JSONObject> boundaries) {
//		List<JSONObject> result = new ArrayList<>();
//		for(JSONObject obj : boundaries) {
//			if(PLACES.contains(obj.getJSONObject("properties").optString("place"))) {
//				result.add(obj);
//			}
//		}
//		return result;
//	}
//
//	public static List<JSONObject> filterAdminBoundaries(List<JSONObject> boundaries) {
//		List<JSONObject> result = new ArrayList<>();
//		for(JSONObject obj : boundaries) {
//			if(obj.getJSONObject("properties").has("admin_level") && "administrative".equals(obj.getJSONObject("properties").optString("boundary"))) {
//				result.add(obj);
//			}
//		}
//		return result;
//	}
	
	public static Map<String, String> filterNameTags(JSONObject obj) {
		
		JSONObject properties = obj.optJSONObject("properties");
		if(properties == null) {
			properties = obj;
		}
		
		Map<String, String> result = new HashMap<String, String>();
		
		@SuppressWarnings("unchecked")
		Iterator<String> i = properties.keys();
		while(i.hasNext()) {
			String key = i.next();
			
			if(key.contains("name")) {
				result.put(key, properties.getString(key));
			}
		}
		
		return result;
	}
}
