package me.osm.gazetter.out;

import java.util.Collection;
import java.util.Map;

import org.json.JSONObject;

public interface AddrRowValueExtractor {

	public String getValue(String key, JSONObject jsonObject, Map<String, JSONObject> levels, JSONObject addrRow);

	public Collection<String> getSupportedKeys();

}
