package me.osm.gazetter.striper;

import static me.osm.gazetter.utils.FileUtils.getFileIS;
import me.osm.gazetter.striper.builders.Builder;
import me.osm.gazetter.striper.readers.PointsReader;
import me.osm.gazetter.striper.readers.RelationsReader;
import me.osm.gazetter.striper.readers.WaysReader;

public class Engine {
	
	public void filter(String datatDir, Builder... builders) {
		String nodes = datatDir + "/" + "nodes.osm";
		String ways = datatDir + "/" + "ways.osm";
		String rels = datatDir + "/" + "rels.osm";

		try {
			new RelationsReader().read(getFileIS(rels), builders);
			for(Builder builder : builders) {
				builder.firstRunDoneRelations();
			}
			
			new WaysReader().read(getFileIS(ways), builders);
			for(Builder builder : builders) {
				builder.firstRunDoneWays();
			}

			for(Builder builder : builders) {
				builder.beforeLastRun();
			}
			
			new PointsReader(builders).read(getFileIS(nodes), builders);
			new WaysReader(builders).read(getFileIS(ways), builders);
			new WaysReader(builders).read(getFileIS(rels), builders);
			
			for(Builder builder : builders) {
				builder.afterLastRun();
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
