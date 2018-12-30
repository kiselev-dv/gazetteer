package me.osm.gazetteer.out;

import java.util.Collection;
import java.util.Map;

import org.json.JSONObject;

public interface AddrRowValueExtractor {

	public Object getValue(String key, JSONObject jsonObject, Map<String, JSONObject> levels, JSONObject addrRow);

	public Collection<String> getSupportedKeys();

	public boolean supports(String key);

}
