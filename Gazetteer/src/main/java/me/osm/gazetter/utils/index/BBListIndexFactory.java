package me.osm.gazetter.utils.index;

public class BBListIndexFactory implements IndexFactory {

	@Override
	public BinaryIndex newByteIndex(int rowLength) {
		return new ByteBufferList(rowLength);
	}

}
