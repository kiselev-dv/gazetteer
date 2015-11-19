package me.osm.gazetter;

import java.io.IOException;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;

/**
 * Program and libraries versions holder
 * */
public class Versions {
	
	private static final Properties file = new Properties();
	static { 
		try {
			file.load(Versions.class.getResourceAsStream("/version.properties"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * App version
	 */
	public static final String gazetteer = StringUtils.replace(file.getProperty("app.version"), "-SNAPSHOT", "b");
	
	
	/**
	 * JavaTopologySuite version 
	 */
	public static final String jts = file.getProperty("jts.version");
	
	/**
	 * osm-doc-java version
	 * {@link "https://github.com/kiselev-dv/osm-doc-java"}
	 * */
	public static final String osmdoc = file.getProperty("osmdoc.version");
	
	/**
	 * Groovy runtime version
	 * */
	public static final String groovy = file.getProperty("groovy.version");
	
	/**
	 * Application build timestamp
	 * */
	public static final String buildTs = file.getProperty("build.timestamp");
}
