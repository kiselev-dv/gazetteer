package me.osm.gazetter.addresses;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.json.JSONObject;

/**
 * Utilities for address parsing.
 * */
public class AddressesUtils {
	
	/**
	 * Transliterate (fold) string
	 * 
	 * Replace characters which are outside standard ASCII set
	 * 
	 * @param string in
	 * @return folded string
	 * */
	public static String foldASCII(String string) {
		
		char[] charArray = string.toCharArray();
		char[] out = new char[charArray.length * 4 + 1];
		int outLength = ASCIIFoldingFilter.foldToASCII(charArray, 0, out, 0, charArray.length);
		
		return String.copyValueOf(out, 0, outLength);
	}
	
	/**
	 * Find tags with '*name*' key
	 * 
	 * @param obj subject or properties
	 * 
	 * @return tag-key:tag-val map 
	 * */
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
	
	/**
	 * Look for name:lc tags and return them as JSONObject
	 * 
	 * @param properties Original properties
	 * @param langs which lang codes we are looking for
	 * 
	 * @return JSONObject with filtered properties
	 * */
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
