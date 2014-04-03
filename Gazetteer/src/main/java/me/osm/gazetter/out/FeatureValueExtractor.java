package me.osm.gazetter.out;

import org.json.JSONObject;

public interface FeatureValueExtractor {
	public String getValue(String key, JSONObject jsonObject);
}
