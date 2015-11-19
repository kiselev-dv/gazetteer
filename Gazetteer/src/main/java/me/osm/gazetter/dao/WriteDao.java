package me.osm.gazetter.dao;

import java.io.IOException;

/**
 * Abstraction for writing in multiple destinations
 * */
public interface WriteDao {
	
	/**
	 * Write line into specified by key container
	 * 
	 * @param line to write
	 * @param key container key
	 * 
	 * @throws IOException if write fails
	 * */
	public void write(String line, String key) throws IOException;
	
	
	/**
	 * Free resources
	 */
	public void close();
}
