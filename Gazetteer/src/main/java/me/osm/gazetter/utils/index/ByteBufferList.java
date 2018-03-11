package me.osm.gazetter.utils.index;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class ByteBufferList implements BinaryIndex {
	
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
	public int find(final long search, final Accessor accessor, IndexLineAccessMode mode) {
		
		return Collections.binarySearch(storage, null,
				new Comparator<ByteBuffer>() {
					
					@Override
					public int compare(ByteBuffer row, ByteBuffer key) {
						return Long.compare(accessor.get(row), search);
					}
					
				});
	}
	
	public int find(final ByteBuffer search, final BBAccessor accessor) {
		final byte[] sa = search.array();
		
		return Collections.binarySearch(storage, null,
				new Comparator<ByteBuffer>() {
					
					@Override
					public int compare(ByteBuffer row, ByteBuffer key) {
						byte[] b1 = accessor.get(row).array();
						
						return compareByteArrays(sa, b1);
					}
					
				});
	}
	
	@Override
	public ByteBuffer get(int i, IndexLineAccessMode mode)  {
		ByteBuffer row = storage.get(i);

		if (mode == IndexLineAccessMode.UNLINKED) {
			return ByteBuffer.wrap(row.array());
		}
		
		return row;
	}

	@Override
	public List<ByteBuffer> findAll(int index, long id, Accessor accessor, IndexLineAccessMode mode) {
		List<ByteBuffer> result = new ArrayList<ByteBuffer>();
		
		if(index >= 0 ) {
			result.add(get(index, mode));
			for(int i = 1; ;i++) {

				boolean lp = false;
				boolean ln = false;
				
				ByteBuffer lineP = getSafe(index + i, mode);
				if(lineP != null && accessor.get(lineP) == id) {
					result.add(lineP);
					lp = true;
				}

				ByteBuffer lineN = getSafe(index - i, mode);
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
	
	public static int compareByteArrays(final byte[] array1, byte[] array2) {
		if(array2.length != array1.length) {
			throw new RuntimeException();
		}
		
		for (int i = 0; i < array2.length && i < array1.length; i++) {
			byte b = array2[i];
			byte s = array1[i];
			int c = Byte.compare(b, s);
			if (c != 0) {
				return c;
			}
		}
		return 0;
	}
	
	protected ByteBuffer getSafe(int i, IndexLineAccessMode mode) {
		if(i >= 0 && i < size()) {
			return get(i, mode);
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

	@Override
	public void close() {
		
	}

	@Override
	public void synchronize() {
		
	}
	
}
