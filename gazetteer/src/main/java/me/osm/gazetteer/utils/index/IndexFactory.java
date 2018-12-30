package me.osm.gazetteer.utils.index;

public interface IndexFactory {

	public BinaryIndex newByteIndex(int rowLength);

}
