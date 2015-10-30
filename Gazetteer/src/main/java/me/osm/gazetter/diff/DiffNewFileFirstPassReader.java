package me.osm.gazetter.diff;

import java.io.PrintWriter;
import java.util.Set;
import java.util.TreeMap;

import me.osm.gazetter.striper.GeoJsonWriter;
import me.osm.gazetter.utils.FileUtils.LineHandler;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;

public final class DiffNewFileFirstPassReader implements LineHandler {
	
	private final Set<String> olds;
	private final Counters counters;

	private TreeMap<String, Object[]> map;
	private PrintWriter outTmp;
	
	public DiffNewFileFirstPassReader(TreeMap<String, Object[]> map,  
			PrintWriter outTmp, Set<String> olds, Counters counters) {
		this.olds = olds;
		this.counters = counters;
		this.map = map;
		this.outTmp = outTmp;
	}

	@Override
	public void handle(String s) {
		if(StringUtils.isEmpty(s)) {
			return;
		}
		
		String id = GeoJsonWriter.getId(s);
		DateTime timestamp = GeoJsonWriter.getTimestamp(s);
		String md5 = GeoJsonWriter.getMD5(s);
		
		counters.newHash = counters.newHash ^ s.hashCode();
		counters.newTs = counters.newTs.isAfter(timestamp) ? counters.newTs : timestamp;
		
		Object[] row = map.get(id);
		if(row == null) {
			outTmp.println("+ " + s);
			counters.add++;
		}
		else {
			if (!((String)row[0]).equals(md5)) {
				DateTime old = (DateTime)row[1];
				if(old.isBefore(timestamp)) {
					outTmp.println("N " + s);
					counters.takeNew++;
				}
				else {
					olds.add(id);
				}
			}
		}
		
		map.remove(id);
		
	}
}