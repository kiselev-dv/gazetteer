package me.osm.gazetter.addresses;

import java.util.List;

import org.json.JSONObject;

public interface AddressesSchemesParser {
	
	public static final String ADDR_SCHEME = "addr-scheme";

	List<JSONObject> parseSchemes(JSONObject properties);
}
