package me.osm.gazetter;

import java.io.IOException;
import java.util.Properties;

public class Versions {
	
	private static final Properties file = new Properties();
	static { 
		try {
			file.load(Versions.class.getResourceAsStream("version.properties"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static final String gazetteer = file.getProperty("app.version");
}
