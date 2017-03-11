package me.osm.gazetter.diff.readers;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.osm.gazetter.diff.Counters;
import me.osm.gazetter.diff.indx.DiffMapIndex;
import me.osm.gazetter.diff.indx.DiffMapIndex.DiffMapIndexRow;
import me.osm.gazetter.striper.GeoJsonWriter;
import me.osm.gazetter.utils.FileUtils.LineHandler;

/**
 * Read Old file and get timestamps and md5 hashes of strings
 * and store them into the map
 * 
 * @author dkiselev
 */
public final class DiffOldFileFirstPassReader implements LineHandler {
	
	private static final Logger log = LoggerFactory.getLogger(DiffOldFileFirstPassReader.class);
	
	private final Counters counters;
	private DiffMapIndex map;
	
	/**
	 * @param map2 where to store result
	 * @param counters
	 */
	public DiffOldFileFirstPassReader(DiffMapIndex mapIndex, Counters counters) {
		this.counters = counters;
		this.map = mapIndex;
	}

	@Override
	public void handle(String s) {
		
		if (s.contains("\"type\":\"mtainf\"")) {
			JSONObject meta = new JSONObject(s);
			counters.oldHash = meta.getString("hash");
			counters.oldTs =  GeoJsonWriter.getTimestamp(s);
		}
		else {
			DiffMapIndexRow row = new DiffMapIndexRow();

			row.key = GeoJsonWriter.getId(s);
			row.timestamp = GeoJsonWriter.getTimestamp(s);
			row.hash = GeoJsonWriter.getMD5(s).hashCode();
			
			DiffMapIndexRow previous = map.put(row);
			if (previous != null) {
				log.warn("Different lines with the same id: {}", 
						row.key);
			}
		}
	}
}