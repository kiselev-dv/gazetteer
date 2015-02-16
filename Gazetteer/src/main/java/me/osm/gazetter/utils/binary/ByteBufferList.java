package me.osm.gazetter.utils.binary;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class ByteBufferList implements BinaryBuffer {
	
	private final List<ByteBuffer> storage = new ArrayList<>();
	
	private int rowLength = 0;
	
	public ByteBufferList(int rowLength) {
		this.rowLength = rowLength;
	}
	
	@Override
	public void add(ByteBuffer bb) {
		try {
			assert bb.array().length == rowLength;
			
			storage.add(bb);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public synchronized void sort(Comparator<ByteBuffer> comparator) {
		Collections.sort(storage, comparator);
	}

	@Override
	public int find(final long search, final Accessor accessor) {
		
		return Collections.binarySearch(storage, null,
				new Comparator<ByteBuffer>() {
					
					@Override
					public int compare(ByteBuffer row, ByteBuffer key) {
						return Long.compare(accessor.get(row), search);
					}
					
				});
	}
	
	@Override
	public ByteBuffer get(int i)  {
		return storage.get(i);
	}

	@Override
	public List<ByteBuffer> findAll(int index, long id, Accessor accessor) {
		List<ByteBuffer> result = new ArrayList<ByteBuffer>();
		
		if(index >= 0 ) {
			result.add(storage.get(index));
			for(int i = 1; ;i++) {

				boolean lp = false;
				boolean ln = false;
				
				ByteBuffer lineP = getSafe(storage, index + i);
				if(lineP != null && accessor.get(lineP) == id) {
					result.add(lineP);
					lp = true;
				}

				ByteBuffer lineN = getSafe(storage, index - i);
				if(lineN != null && accessor.get(lineN) == id) {
					result.add(lineN);
					ln = true;
				}
				
				if(!lp && !ln) {
					break;
				}
				
			}
		}
		
		return result;
	}
	
	private static ByteBuffer getSafe(List<ByteBuffer> collection, int i) {
		if(i >= 0 && i < collection.size()) {
			return collection.get(i);
		}
		return null;
	}

	@Override
	public Iterator<ByteBuffer> iterator() {
		return storage.iterator();
	}

	@Override
	public int size() {
		return storage.size();
	}
	
}
