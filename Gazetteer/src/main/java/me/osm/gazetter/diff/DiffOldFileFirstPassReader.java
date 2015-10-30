package me.osm.gazetter.diff;

import java.util.TreeMap;

import me.osm.gazetter.striper.GeoJsonWriter;
import me.osm.gazetter.utils.FileUtils.LineHandler;

import org.joda.time.DateTime;

public final class DiffOldFileFirstPassReader implements LineHandler {
	
	private final Counters counters;
	private TreeMap<String, Object[]> map;
	
	public DiffOldFileFirstPassReader(TreeMap<String, Object[]> map, Counters counters) {
		this.counters = counters;
		this.map = map;
	}

	@Override
	public void handle(String s) {
		String id = GeoJsonWriter.getId(s);
		DateTime timestamp = GeoJsonWriter.getTimestamp(s);
		String md5 = GeoJsonWriter.getMD5(s);
		
		counters.oldHash = counters.oldHash ^ s.hashCode();
		counters.oldTs = counters.oldTs.isAfter(timestamp) ? counters.oldTs : timestamp;
		
		map.put(id, new Object[]{md5, timestamp});
	}
}