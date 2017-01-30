package me.osm.gazetter.diff;

import java.io.PrintWriter;
import java.util.Set;
import java.util.TreeMap;

import me.osm.gazetter.striper.GeoJsonWriter;
import me.osm.gazetter.utils.FileUtils.LineHandler;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.json.JSONObject;

/**
 * Read New file and get timestamps and md5 hashes of strings
 * if map (prefiled with same values from old file) doesnt 
 * contains same object id - write add instruction into ooutput
 * 
 * @author dkiselev
 */
public final class DiffNewFileFirstPassReader implements LineHandler {
	
	private final Set<String> olds;
	private final Counters counters;

	private TreeMap<String, Object[]> map;
	private PrintWriter outTmp;
	
	/**
	 * @param map with ids and timestamps from old 
	 * @param outTmp output writer
	 * @param olds old object ids
	 * @param counters 
	 */
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
		
		if(s.contains("\"type\":\"mtainf\"")) {
			counters.newHash = (new JSONObject(s).getString("hash")); 
			counters.newTs = GeoJsonWriter.getTimestamp(s);
		}
		else {
			
			String id = GeoJsonWriter.getId(s);
			DateTime timestamp = GeoJsonWriter.getTimestamp(s);
			String md5 = GeoJsonWriter.getMD5(s);
			
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
}