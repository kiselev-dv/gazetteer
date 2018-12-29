package me.osm.gazetteer.diff.readers;

import java.io.PrintWriter;
import java.util.Set;

import me.osm.gazetteer.diff.Counters;
import me.osm.gazetteer.diff.indx.DiffMapIndex;
import me.osm.gazetteer.striper.GeoJsonWriter;
import me.osm.gazetteer.utils.FileUtils.LineHandler;

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

	private DiffMapIndex map;
	private PrintWriter outTmp;

	/**
	 * @param mapIndex with ids timestamps and hashes
	 * @param outTmp where to write results
	 * @param olds ids to write
	 * @param counters
	 */
	public DiffOldFileSecondPassReader(DiffMapIndex mapIndex,
			PrintWriter outTmp, Set<String> olds, Counters counters) {

		this.olds = olds;
		this.counters = counters;
		this.map = mapIndex;
		this.outTmp = outTmp;
	}

	@Override
	public void handle(String s) {
		String id = GeoJsonWriter.getId(s);

		if(map.get(id) != null) {
			outTmp.println("- " + s);
			counters.remove++;
		}

		else if(olds.contains(id)) {
			outTmp.println("O " + s);
			counters.takeOld++;
		}
	}
}
