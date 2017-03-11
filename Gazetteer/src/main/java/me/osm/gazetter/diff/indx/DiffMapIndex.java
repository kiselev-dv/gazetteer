package me.osm.gazetter.diff.indx;

import java.util.Iterator;

import org.joda.time.DateTime;

public interface DiffMapIndex {

	public static final class DiffMapIndexRow {
		public String key;
		public int hash;
		public DateTime timestamp;
	} 
	
	public int size();

	public DiffMapIndexRow put(DiffMapIndexRow row);

	public DiffMapIndexRow get(String id);

	public boolean areHashesEquals(DiffMapIndexRow rowOld, DiffMapIndexRow rowNew);

	public void remove(String key);

	public void buildIndex();

	public void cleanRemoved();

	public Iterator<DiffMapIndexRow> rowsIterator();

}
