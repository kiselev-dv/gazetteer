package me.osm.gazetteer.diff.readers;

import java.io.PrintWriter;
import java.util.Set;

import me.osm.gazetteer.diff.indx.DiffMapIndex;
import me.osm.gazetteer.diff.Counters;
import me.osm.gazetteer.striper.GeoJsonWriter;
import me.osm.gazetteer.utils.FileUtils.LineHandler;

import org.apache.commons.lang3.StringUtils;
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

	private DiffMapIndex map;
	private PrintWriter outTmp;

	/**
	 * @param mapIndex with ids and timestamps from old
	 * @param outTmp output writer
	 * @param olds old object ids
	 * @param counters
	 */
	public DiffNewFileFirstPassReader(DiffMapIndex mapIndex,
			PrintWriter outTmp, Set<String> olds, Counters counters) {
		this.olds = olds;
		this.counters = counters;
		this.map = mapIndex;
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

			DiffMapIndex.DiffMapIndexRow rowNew = new DiffMapIndex.DiffMapIndexRow();
			rowNew.key = GeoJsonWriter.getId(s);
			rowNew.timestamp = GeoJsonWriter.getTimestamp(s);
			rowNew.hash = GeoJsonWriter.getMD5(s).hashCode();

			DiffMapIndex.DiffMapIndexRow rowOld = map.get(rowNew.key);
			if(rowOld == null) {
				outTmp.println("+ " + s);
				counters.add++;
			}
			else {
				if (! map.areHashesEquals(rowOld, rowNew)) {
					if(rowOld.timestamp.isBefore(rowNew.timestamp)) {
						outTmp.println("N " + s);
						counters.takeNew++;
					}
					else {
						olds.add(rowNew.key);
					}
				}
			}

			map.remove(rowNew.key);
		}

	}
}
