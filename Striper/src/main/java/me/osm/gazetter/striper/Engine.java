package me.osm.gazetter.striper;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import me.osm.gazetter.striper.builders.Builder;
import me.osm.gazetter.striper.readers.ComplexReader;
import me.osm.gazetter.striper.readers.PointsReader;
import me.osm.gazetter.striper.readers.RelationsReader;
import me.osm.gazetter.striper.readers.WaysReader;

public class Engine {
	
	public void filter(String osmFilePath, Builder... builders) {
		try {
			
			new RelationsReader().read(getFileIS(osmFilePath), builders);
			for(Builder builder : builders) {
				builder.firstRunDoneRelations();
			}
			
			new WaysReader().read(getFileIS(osmFilePath), builders);
			for(Builder builder : builders) {
				builder.firstRunDoneWays();
			}

			for(Builder builder : builders) {
				builder.beforeLastRun();
			}
			
			new ComplexReader(
				new PointsReader(builders),
				new WaysReader(builders),
				new RelationsReader(builders)
			).read(getFileIS(osmFilePath));
			
			for(Builder builder : builders) {
				builder.afterLastRun();
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private InputStream getFileIS(String osmFilePath) throws IOException,
			FileNotFoundException {
		if(osmFilePath.endsWith("gz")) {
			return new GZIPInputStream(new FileInputStream(osmFilePath));
		}
		return new FileInputStream(osmFilePath);
	}
}
