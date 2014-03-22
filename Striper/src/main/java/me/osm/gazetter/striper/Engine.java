package me.osm.gazetter.striper;

import static me.osm.gazetter.utils.FileUtils.getFileIS;
import me.osm.gazetter.striper.builders.Builder;
import me.osm.gazetter.striper.readers.PointsReader;
import me.osm.gazetter.striper.readers.RelationsReader;
import me.osm.gazetter.striper.readers.WaysReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Engine {
	
	private static final Logger log = LoggerFactory.getLogger(Engine.class);
	
	public void filter(String datatDir, Builder... builders) {
		String nodes = datatDir + "/" + "nodes.osm";
		String ways = datatDir + "/" + "ways.osm";
		String rels = datatDir + "/" + "rels.osm";

		try {
			new RelationsReader().read(getFileIS(rels), builders);
			log.info("First run: done relations.");
			for(Builder builder : builders) {
				builder.firstRunDoneRelations();
			}

			new WaysReader().read(getFileIS(ways), builders);
			log.info("First run: done ways.");
			for(Builder builder : builders) {
				builder.firstRunDoneWays();
			}

			new PointsReader(builders).read(getFileIS(nodes), builders);
			log.info("First run: done nodes.");
			for(Builder builder : builders) {
				builder.firstRunDoneNodes();
			}

			new WaysReader(builders).read(getFileIS(ways), builders);
			log.info("Second run: done ways.");
			for(Builder builder : builders) {
				builder.secondRunDoneWays();
			}
			
			new RelationsReader(builders).read(getFileIS(rels), builders);
			log.info("Second run: done relations.");
			for(Builder builder : builders) {
				builder.secondRunDoneRelations();
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
