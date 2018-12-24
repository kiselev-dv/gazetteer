package me.osm.gazetteer.diff.readers;

import me.osm.gazetteer.diff.indx.DiffMapIndex;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.osm.gazetteer.diff.Counters;
import me.osm.gazetteer.striper.GeoJsonWriter;
import me.osm.gazetteer.utils.FileUtils.LineHandler;

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
			DiffMapIndex.DiffMapIndexRow row = new DiffMapIndex.DiffMapIndexRow();

			row.key = GeoJsonWriter.getId(s);
			row.timestamp = GeoJsonWriter.getTimestamp(s);
			row.hash = GeoJsonWriter.getMD5(s).hashCode();

			DiffMapIndex.DiffMapIndexRow previous = map.put(row);
			if (previous != null) {
				log.warn("Different lines with the same id: {}",
						row.key);
			}
		}
	}
}
