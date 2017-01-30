package me.osm.gazetter.utils.index;

public interface IndexFactory {
	
	public BinaryIndex newByteIndex(int rowLength);
	
}
