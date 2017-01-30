package me.osm.gazetter.diff;

import java.util.TreeMap;

import me.osm.gazetter.striper.GeoJsonWriter;
import me.osm.gazetter.utils.FileUtils.LineHandler;

import org.joda.time.DateTime;
import org.json.JSONObject;

/**
 * Read Old file and get timestamps and md5 hashes of strings
 * and store them into the map
 * 
 * @author dkiselev
 */
public final class DiffOldFileFirstPassReader implements LineHandler {
	
	private final Counters counters;
	private TreeMap<String, Object[]> map;
	
	/**
	 * @param map where to store result
	 * @param counters
	 */
	public DiffOldFileFirstPassReader(TreeMap<String, Object[]> map, Counters counters) {
		this.counters = counters;
		this.map = map;
	}

	@Override
	public void handle(String s) {
		
		if (s.contains("\"type\":\"mtainf\"")) {
			JSONObject meta = new JSONObject(s);
			counters.oldHash = meta.getString("hash");
			counters.oldTs =  GeoJsonWriter.getTimestamp(s);
		}
		else {
			String id = GeoJsonWriter.getId(s);
			DateTime timestamp = GeoJsonWriter.getTimestamp(s);
			String md5 = GeoJsonWriter.getMD5(s);
			
			map.put(id, new Object[]{md5, timestamp});
		}
	}
}