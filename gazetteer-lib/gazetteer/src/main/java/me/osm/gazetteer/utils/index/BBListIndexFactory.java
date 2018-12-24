package me.osm.gazetteer.utils.index;

public class BBListIndexFactory implements IndexFactory {

	@Override
	public BinaryIndex newByteIndex(int rowLength) {
		return new ByteBufferList(rowLength);
	}

}
