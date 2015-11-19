package me.osm.gazetter.diff;

import java.io.PrintWriter;
import java.util.Set;
import java.util.TreeMap;

import me.osm.gazetter.striper.GeoJsonWriter;
import me.osm.gazetter.utils.FileUtils.LineHandler;

/**
 * Read old file for second time to write info for
 * lines which are older then lines from new file
 * 
 * @author dkiselev
 */
public final class DiffOldFileSecondPassReader implements
		LineHandler {
	
	private final Set<String> olds;
	private final Counters counters;
	
	private TreeMap<String, Object[]> map;
	private PrintWriter outTmp;

	/**
	 * @param map with ids timestamps and hashes
	 * @param outTmp where to write results
	 * @param olds ids to write
	 * @param counters
	 */
	public DiffOldFileSecondPassReader(TreeMap<String, Object[]> map,  
			PrintWriter outTmp, Set<String> olds, Counters counters) {
		
		this.olds = olds;
		this.counters = counters;
		this.map = map;
		this.outTmp = outTmp;
	}

	@Override
	public void handle(String s) {
		String id = GeoJsonWriter.getId(s);
		
		if(map.containsKey(id)) {
			outTmp.println("- " + s);
			counters.remove++;
		}
		
		else if(olds.contains(id)) {
			outTmp.println("O " + s);
			counters.takeOld++;
		}
	}
}