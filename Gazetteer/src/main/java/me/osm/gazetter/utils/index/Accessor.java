package me.osm.gazetter.utils.index;

import java.nio.ByteBuffer;

public interface Accessor {
	
	public long get(ByteBuffer row);
	
}
