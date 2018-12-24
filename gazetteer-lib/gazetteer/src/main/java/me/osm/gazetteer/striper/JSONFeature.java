package me.osm.gazetteer.striper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Wrapper around @see JSONObject.
 *
 * Provides copy capabilities and writes features out with certain
 * order of keys.
 * */
public final class JSONFeature extends JSONObject {

	public static JSONObject copy(JSONObject properties) {

		@SuppressWarnings("unchecked")
		Set<String> keys = properties.keySet();
		return new JSONObject(properties, keys.toArray(new String[keys.size()]));
	}

	public JSONFeature(String line) {
		super(line);
	}

	@Override
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Iterator keys() {

		List<String> keys = new ArrayList<String>(keySet());
		Collections.sort(keys, new Comparator<String>() {
			@Override
			public int compare(String o1, String o2) {
				int i1 = "id".equals(o1) ? 0
						: "ftype".equals(o1) ? 1
						: GeoJsonWriter.TIMESTAMP.equals(o1) ? 2
						: "action".equals(o1) ? 3
						: "md5".equals(o2) ? 4
						: "hhash".equals(o2) ? 5
						: 10;

				int i2 = "id".equals(o2) ? 0
						: "ftype".equals(o2) ? 1
						: GeoJsonWriter.TIMESTAMP.equals(o2) ? 2
						: "action".equals(o2) ? 3
						: "md5".equals(o2) ? 4
						: "hhash".equals(o2) ? 5
						: 10;

				if(i1 == 10 && i2 == 10) {
					return o1.compareTo(o2);
				}

				return i1 - i2;
			}
		});

		return keys.iterator();
	}

	/**
	 * Instanciate copy (not deep, only direct children will be copied)
	 * */
	public JSONFeature (JSONObject obj) {
		super(obj, JSONObject.getNames(obj));
	}

	/**
	 * Instanciate copy (not deep, only direct children will be copied)
	 * but copy only provided keys
	 * */
	public JSONFeature (JSONObject obj, String[] keys) {
		super(obj, JSONObject.getNames(obj));
	}

	/**
	 * Default constructor
	 * */
	public JSONFeature() {
		super();
	}

	/**
	 * Copy id and tags
	 * */
	public static JSONObject asRefer(JSONObject feature) {
		JSONObject result = new JSONObject();
		result.put("id", feature.getString("id"));
		result.put(GeoJsonWriter.PROPERTIES, feature.getJSONObject(GeoJsonWriter.PROPERTIES));
		return result;
	}

	/**
	 * Copy id and tags for collection
	 * */
	public static List<JSONObject> asRefers(Collection<JSONObject> features) {
		List<JSONObject> result = new ArrayList<>();
		if(features != null) {
			for(JSONObject obj : features) {
				result.add(asRefer(obj));
			}
		}
		return result;
	}

	/**
	 * Copy id and tags for collection
	 * */
	public static List<JSONObject> asRefers(JSONArray features) {
		List<JSONObject> result = new ArrayList<>();
		if(features != null) {
			for(int i = 0; i < features.length(); i++) {
				result.add(asRefer(features.getJSONObject(i)));
			}
		}
		return result;
	}

	@Override
	public int hashCode() {
		if (has("id")) {
			if(get("timestamp") != null) {
				return get("id").hashCode() * 123 + get("timestamp").hashCode();
			}
			else {
				return get("id").hashCode();
			}
		}
		else {
			return super.hashCode();
		}
	}

	@Override
	public boolean equals(Object obj) {
		if(obj == null) {
			return false;
		}

		if(obj instanceof JSONFeature) {
			return hashCode() == obj.hashCode();
		}

		return super.equals(obj);
	}

	@SuppressWarnings("unchecked")
	public static void merge(JSONObject result, JSONObject forMerge) {
		for(String key : (Collection<String>)forMerge.keySet()) {
			result.put(key, forMerge.get(key));
		}
	}

}
