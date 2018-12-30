package me.osm.gazetteer.utils.index;

import java.nio.ByteBuffer;

public interface BBAccessor {
	public ByteBuffer get(ByteBuffer row);
}
