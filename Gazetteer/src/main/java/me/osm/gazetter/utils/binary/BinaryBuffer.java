package me.osm.gazetter.utils.binary;

import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.List;

public interface BinaryBuffer extends Iterable<ByteBuffer>{
	
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
	public int find(long key, Accessor accessor);
	
	public List<ByteBuffer> findAll(int index, long key, Accessor accessor);
	
	/**
	 * Get row
	 * */
	public ByteBuffer get(int i);
	
	public int size();
}
