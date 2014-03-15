package me.osm.gazetter.dao;

import java.io.IOException;

public interface WriteDao {
	public void write(String line, String key) throws IOException;
	public void close();
}
