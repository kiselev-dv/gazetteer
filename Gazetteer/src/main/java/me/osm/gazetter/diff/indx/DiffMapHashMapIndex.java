package me.osm.gazetter.diff.indx;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.joda.time.DateTime;

public class DiffMapHashMapIndex implements DiffMapIndex {

	private Map<String, Object[]> map = new HashMap<>();
	
	@Override
	public int size() {
		return map.size();
	}

	@Override
	public DiffMapIndexRow put(DiffMapIndexRow row) {
		Object[] previous = map.put(row.key, new Object[]{row.hash, row.timestamp});

		if (previous != null) {
			DiffMapIndex.DiffMapIndexRow prev = new DiffMapIndex.DiffMapIndexRow();
			prev.key = row.key;
			prev.hash = (Integer)previous[0];
			prev.timestamp = (DateTime) previous[1];
			
			return prev;
		}
		
		return null;
	}

	@Override
	public DiffMapIndexRow get(String id) {
		Object[] row = map.get(id);
		
		if (row != null) {
			DiffMapIndex.DiffMapIndexRow prev = new DiffMapIndex.DiffMapIndexRow();
			prev.key = id;
			prev.hash = (Integer) row[0];
			prev.timestamp = (DateTime) row[1];
			
			return prev;
		}
		
		return null;
	}

	@Override
	public boolean areHashesEquals(DiffMapIndexRow rowOld, DiffMapIndexRow rowNew) {
		return rowOld.hash == rowNew.hash;
	}

	@Override
	public void remove(String key) {
		map.remove(key);
	}

	@Override
	public void buildIndex() {
		// Do nothing
	}

	@Override
	public void cleanRemoved() {
		// Do nothing
	}

	@Override
	public Iterator<DiffMapIndexRow> rowsIterator() {
		final Iterator<Entry<String, Object[]>> iterator = map.entrySet().iterator();
		return new Iterator<DiffMapIndexRow>() {

			@Override
			public boolean hasNext() {
				return iterator.hasNext();
			}

			@Override
			public DiffMapIndexRow next() {
				Entry<String, Object[]> kv = iterator.next();
				DiffMapIndexRow row = new DiffMapIndexRow();
				row.key = kv.getKey();
				row.hash = (Integer) kv.getValue()[0];
				row.timestamp =  (DateTime) kv.getValue()[1];
				return row;
			}
			
		};
	}

}
