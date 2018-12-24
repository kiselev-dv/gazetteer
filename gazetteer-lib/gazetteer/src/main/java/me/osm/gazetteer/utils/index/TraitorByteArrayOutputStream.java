package me.osm.gazetteer.utils.index;

import java.io.ByteArrayOutputStream;

/**
 * I need it to avoid array copy form original toByteArray
 * 'cause i'll have rather big buffers and keep two of them is
 * an overhead
 * */
public class TraitorByteArrayOutputStream extends ByteArrayOutputStream {

	public byte[] getBuf() {
		return buf;
	}

	public int count() {
		return count;
	}

}
