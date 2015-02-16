package me.osm.gazetter.utils.binary;

import java.nio.ByteBuffer;

public interface Accessor {
	
	public long get(ByteBuffer row);
	
}
