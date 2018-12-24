package me.osm.gazetteer.addresses;

import java.util.List;

import org.json.JSONObject;

/**
 * Parse address scheme from tags.
 *
 * @returns copies of original properties with overrided addr:* tags
 * and parsed addr-scheme
 * */
public interface AddressesSchemesParser {

	/**
	 * Name for JSON field to store scheme name
	 * */
	public static final String ADDR_SCHEME = "addr-scheme";

	/**
	 * Return copies of properties with addr-scheme and overrided
	 * addr:housenumber and addr:street
	 *
	 * @param properties
	 * 			Objects properties (tags)
	 *
	 * @return list of parsed chemems
	 * */
	List<JSONObject> parseSchemes(JSONObject properties);
}
