package me.osm.gazetteer.join.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import me.osm.osmdoc.model.Feature;
import me.osm.osmdoc.model.Tag;
import me.osm.osmdoc.model.Tag.Val;
import me.osm.osmdoc.read.tagvalueparsers.TagValueParser;
import me.osm.osmdoc.read.tagvalueparsers.TagsStatisticCollector;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

public class ExportTagsStatisticCollector implements TagsStatisticCollector {

	private static final String PATH_SEPARATOR = "/";

	private static final Map<String, AtomicInteger> stat = new HashMap<String, AtomicInteger>();
	private static final Object MUTEX = new Object();

	@Override
	public void success(Object pv, Tag tag, Val val, String rawValue,
			TagValueParser parser, List<Feature> poiClassess) {

		for(Feature f : poiClassess) {
			String key = getKey(f.getName(), tag.getKey().getValue(), getValueString(pv));
			increment(key);
		}
	}

	@Override
	public void failed(Tag tag, String rawValue, TagValueParser parser,
			List<Feature> poiClassess) {

		for(Feature f : poiClassess) {
			String key = getKey(f.getName(), tag.getKey().getValue(), "_error");
			increment(key);
		}
	}


	private void increment(String key) {
		if(stat.get(key) == null) {
			synchronized (MUTEX) {
				if(stat.get(key) == null) {
					stat.put(key, new AtomicInteger());
				}
			}
		}
		stat.get(key).getAndIncrement();
	}

	private String getKey(String fname, String key, String valueString) {
		StringBuilder sb = new StringBuilder();

		sb.append(PATH_SEPARATOR).append(fname).append(PATH_SEPARATOR).append(key);

		if(StringUtils.isNotBlank(valueString)) {
			sb.append(PATH_SEPARATOR).append(valueString);
		}

		return sb.toString();
	}

	private String getValueString(Object pv) {

		if(pv instanceof String) {
			return (String) pv;
		}

		else if(pv instanceof Boolean) {
			return pv.toString();
		}
		//wh
		else if(pv instanceof JSONObject) {
			if(((JSONObject)pv).optBoolean("24_7")) {
				return "24_7";
			}
			return "_total";
		}

		return "_total";
	}

	public Collection<JSONObject> asJson() {
		Map<String, JSONObject> res = new HashMap<>();

		for(Entry<String, AtomicInteger> entry : stat.entrySet()) {
			String[] split = StringUtils.split(entry.getKey(), PATH_SEPARATOR);
			if(!res.containsKey(split[0])) {

				JSONObject obj = new JSONObject();

				obj.put("name", split[0]);
				obj.put("tags_info", new JSONObject());

				res.put(split[0], obj);
			}

			JSONObject tagInfo = res.get(split[0]).getJSONObject("tags_info").optJSONObject(split[1]);
			if(tagInfo == null) {
				tagInfo = new JSONObject();
				res.get(split[0]).getJSONObject("tags_info").put(split[1], tagInfo);
			}

			if(split.length > 2) {
				tagInfo.put(split[2], entry.getValue().get());
				if(!"_error".equals(split[2])) {
					tagInfo.put("_total", tagInfo.optInt("_total") + entry.getValue().get());
				}
			}
		}

		return res.values();
	}


}
