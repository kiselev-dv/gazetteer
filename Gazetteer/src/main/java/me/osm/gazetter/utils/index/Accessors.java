package me.osm.gazetter.utils.index;

import java.nio.ByteBuffer;

public class Accessors {
	
	private static class LongAccessor implements Accessor {
		
		private int offset;
		
		public LongAccessor(int offset) {
			this.offset = offset;
		}

		@Override
		public long get(ByteBuffer row) {
			return row.getLong(offset);
		}
		
		
	} 
	
	public static Accessor longAccessor(int offset) {
		return new LongAccessor(offset);
	}
	
}
