package me.osm.gazetter.addresses;

import java.util.List;

import org.json.JSONObject;

/**
 * Parse address scheme from tags.
 * 
 * @returns copies of original properties with overrided addr:* tags
 * and parsed addr-scheme
 * */
public interface AddressesSchemesParser {
	
	public static final String ADDR_SCHEME = "addr-scheme";

	List<JSONObject> parseSchemes(JSONObject properties);
}
