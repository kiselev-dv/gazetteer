package me.osm.osmdoc.read.tagvalueparsers;

import org.json.JSONArray;
import org.json.JSONObject;

public interface TagValueParser {

	/**
	 * Parse raw tag value
	 * @returns String, Date, Boolean, Integer, Double, {@link JSONArray}, {@link JSONObject} or null.
	 * */
	public Object parse(String rawValue);

}
