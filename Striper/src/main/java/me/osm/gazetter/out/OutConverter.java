package me.osm.gazetter.out;

import me.osm.gazetter.utils.FileUtils.LineHandler;

public interface OutConverter extends LineHandler {

	void close();

}
