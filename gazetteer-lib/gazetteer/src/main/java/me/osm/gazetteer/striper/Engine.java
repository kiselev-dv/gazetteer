package me.osm.gazetteer.striper;

import java.io.File;
import java.util.HashSet;

import me.osm.gazetteer.striper.readers.PointsReader;
import me.osm.gazetteer.striper.readers.RelationsReader;
import me.osm.gazetteer.striper.readers.WaysReader;
import me.osm.gazetteer.utils.FileUtils;
import me.osm.gazetteer.striper.builders.Builder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs reading of nodes, ways and relation files
 * */
public class Engine {

	private static final Logger log = LoggerFactory.getLogger(Engine.class);

	public void filter(HashSet<String> drop, String datatDir, Builder... builders) {
		File nodes = FileUtils.withGz(new File(datatDir + "/" + "nodes.osm"));
		File ways = FileUtils.withGz(new File(datatDir + "/" + "ways.osm"));
		File rels = FileUtils.withGz(new File(datatDir + "/" + "rels.osm"));

		try {
			new RelationsReader(drop).read(FileUtils.getFileIS(rels), builders);
			log.info("First run: done relations.");
			for(Builder builder : builders) {
				builder.firstRunDoneRelations();
			}

			new WaysReader(drop).read(FileUtils.getFileIS(ways), builders);
			log.info("First run: done ways.");
			for(Builder builder : builders) {
				builder.firstRunDoneWays();
			}

			PointsReader pr = new PointsReader(drop);
			pr.read(FileUtils.getFileIS(nodes), builders);
			log.info("First run: done nodes.");
			for(Builder builder : builders) {
				builder.firstRunDoneNodes();
			}
			log.info("Yongest known timestamp of a node: " + pr.getLastNodeTimestamp());

			new WaysReader(drop).read(FileUtils.getFileIS(ways), builders);
			log.info("Second run: done ways.");
			for(Builder builder : builders) {
				builder.secondRunDoneWays();
			}

			new RelationsReader(drop).read(FileUtils.getFileIS(rels), builders);
			log.info("Second run: done relations.");
			for(Builder builder : builders) {
				builder.secondRunDoneRelations();
			}

		} catch (Exception e) {
			throw new RuntimeException("Parsing failed. Data dir: " + datatDir, e);
		}
	}
}
