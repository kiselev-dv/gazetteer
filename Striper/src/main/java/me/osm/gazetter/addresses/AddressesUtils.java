package me.osm.gazetter.addresses;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.json.JSONObject;

public class AddressesUtils {
	
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
