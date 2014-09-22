package me.osm.gazetter.addresses;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.json.JSONObject;

/**
 * Utilities for address parsing.
 * */
public class AddressesUtils {
	
	public static Map<String, String> filterNameTags(JSONObject obj) {
		
		Map<String, String> result = new HashMap<String, String>();

		if(obj != null) {

			JSONObject properties = obj.optJSONObject("properties");
			if(properties == null) {
				properties = obj;
			}
			
			@SuppressWarnings("unchecked")
			Iterator<String> i = properties.keys();
			while(i.hasNext()) {
				String key = i.next();
				
				if(key.contains("name")) {
					result.put(key, properties.getString(key));
				}
			}
		}
		
		return result;
	}
	
	public static JSONObject getNamesTranslations(JSONObject properties,
			Set<String> langs) {
		
		JSONObject translations = new JSONObject(); 
		
		Map<String, String> altNames = AddressesUtils.filterNameTags(properties);
		
		for(String lang : langs) {
			String translated = altNames.get("name:" + lang);
			translations.put("name:" + lang, translated);
		}
		
		return translations;
	}
}
