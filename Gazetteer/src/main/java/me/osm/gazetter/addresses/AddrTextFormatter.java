package me.osm.gazetter.addresses;

import java.util.List;

import org.json.JSONObject;

/**
 * Generate full address string from addr levels
 * */
public interface AddrTextFormatter {

	String joinNames(List<JSONObject> addrJsonRow, JSONObject properties, String lang);

	String joinBoundariesNames(List<JSONObject> result, String lang);

}
