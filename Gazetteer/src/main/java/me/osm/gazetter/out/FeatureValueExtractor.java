package me.osm.gazetter.out;

import java.util.Collection;

import org.json.JSONObject;

public interface FeatureValueExtractor {
	public Object getValue(String key, JSONObject jsonObject);
	public Collection<String> getSupportedKeys();
	public boolean supports(String key);
}
