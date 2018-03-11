package me.osm.gazetter.utils.index;

import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.List;

public interface BinaryIndex extends Iterable<ByteBuffer> {

	/**
	 * Defines access mode for rows in index
	 * 
	 * LINKED - content of returned ByteBuffer is linked to whole index.
	 *          So changes in line will be propagated into the index
	 * 
	 * UNLINKED - changes in byte buffer will not affect data in index
	 * 
	 * IGNORE - index may ignore access type
	 * */
	public static enum IndexLineAccessMode {
		LINKED, 
		UNLINKED, 
		IGNORE};
	
	public static final class BinaryIndexException extends RuntimeException {
		
		private static final long serialVersionUID = -872105555898885765L;

		public BinaryIndexException(String msg, Exception e) {
			super(msg, e);
		}

		public BinaryIndexException(String msg) {
			super(msg);
		}
	}
	
	/**
	 * Add new row.
	 * */
	public void add(ByteBuffer bb);
	
	/**
	 * Sort rows.
	 * */
	public void sort(Comparator<ByteBuffer> comparator);
	
	/**
	 * Binary search for given key.
	 * */
	public int find(long key, Accessor accessor, 
			IndexLineAccessMode mode) throws BinaryIndexException;
	
	public List<ByteBuffer> findAll(int index, 
			long key, Accessor accessor, 
			IndexLineAccessMode mode) throws BinaryIndexException;
	
	/**
	 * Get row
	 * */
	public ByteBuffer get(int i, IndexLineAccessMode mode) throws BinaryIndexException;
	
	/**
	 * Returns number of stored lines
	 * */
	public int size();
	
	/**
	 * Free all resources
	 * */
	public void close();
	
	/**
	 * Intermediate index save
	 * */
	public void synchronize();
}
