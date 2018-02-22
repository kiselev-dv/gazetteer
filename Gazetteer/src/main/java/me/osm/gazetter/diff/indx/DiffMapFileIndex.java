package me.osm.gazetter.diff.indx;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.collections4.iterators.IteratorChain;
import org.joda.time.DateTime;

import me.osm.gazetter.diff.indx.ByteUtils.IdParts;
import me.osm.gazetter.utils.index.BBAccessor;
import me.osm.gazetter.utils.index.ByteBufferList;

public class DiffMapFileIndex implements DiffMapIndex {

	private static final KeyAccessor KEY_ACCESSOR = new KeyAccessor();

	private static final class KeyAccessor implements BBAccessor {
		@Override
		public ByteBuffer get(ByteBuffer row) {
			ByteBuffer keyBB = ByteBuffer.allocate(keyLenght);
			((Buffer)row).position(rowLenght - keyLenght);
			keyBB.put(row);
			((Buffer)keyBB).rewind();
			return keyBB;
		}
	}

	private Map<String, ByteBufferList> bbindexByType = new LinkedHashMap<>();
	
	private static final int keyLenght = 25;
	private static final int rowLenght = keyLenght + 1 + 4 + 8;
	
	public DiffMapFileIndex() {
		
	}
	
	@Override
	public int size() {
		int size = 0;
		for (ByteBufferList bbi : bbindexByType.values()) {
			size += bbi.size();
		}
		return size;
	}

	@Override
	public DiffMapIndexRow put(DiffMapIndexRow row) {
		
		IdParts parsedId = ByteUtils.parse(row.key);
		ByteBufferList bbindex = getIndex(parsedId.type);
		
		ByteBuffer bb = ByteBuffer.allocate(rowLenght);
		
		ByteBuffer encoded = ByteUtils.encode(parsedId);
		((Buffer)encoded).rewind();
		
		// removed | hash | timestamp | id
		bb.put((byte) 0).putInt(row.hash).putLong(row.timestamp.getMillis()).put(encoded);
		bbindex.add(bb);
		
		return null;
	}

	private ByteBufferList getIndex(String type) {
		if(!bbindexByType.containsKey(type)) {
			bbindexByType.put(type, new ByteBufferList(rowLenght));
		}
		return bbindexByType.get(type);
	}

	@Override
	public DiffMapIndexRow get(String id) {
		IdParts idParts = ByteUtils.parse(id);
		ByteBuffer bbid = ByteUtils.encode(idParts); 
		ByteBufferList bbindex = getIndex(idParts.type);
		
		int index = bbindex.find(bbid, KEY_ACCESSOR);
		
		if (index >= 0) {
			DiffMapIndexRow ir = new DiffMapIndexRow();
			ByteBuffer byteBuffer = bbindex.get(index);
			IdParts idPartsDecoded = ByteUtils.decode(KEY_ACCESSOR.get(byteBuffer), idParts.type);
			ir.key = ByteUtils.joinToId(idPartsDecoded);
			((Buffer)byteBuffer).position(0);
			byteBuffer.get();
			ir.hash = byteBuffer.getInt();
			ir.timestamp = new DateTime(byteBuffer.getLong());
			
			return ir;
		}
		
		return null;
	}

	@Override
	public boolean areHashesEquals(DiffMapIndexRow rowOld, DiffMapIndexRow rowNew) {
		return rowOld.hash == rowNew.hash;
	}

	@Override
	public void remove(String key) {
		IdParts idParts = ByteUtils.parse(key);
		ByteBuffer bbid = ByteUtils.encode(idParts); 
		ByteBufferList bbindex = getIndex(idParts.type);
		
		int index = bbindex.find(bbid, KEY_ACCESSOR);
		if (index >= 0) {
			ByteBuffer row = bbindex.get(index);
			row.put((byte) -128);
		}
	}

	@Override
	public void buildIndex() {
		for (ByteBufferList bblist : bbindexByType.values()) {
			bblist.sort(new Comparator<ByteBuffer>() {
				
				@Override
				public int compare(ByteBuffer o1, ByteBuffer o2) {
					ByteBuffer keyBB1 = KEY_ACCESSOR.get(o1);
					ByteBuffer keyBB2 = KEY_ACCESSOR.get(o2);
					
					return ByteBufferList.compareByteArrays(keyBB1.array(), keyBB2.array());
				}
				
			});
		}
	}

	@Override
	public void cleanRemoved() {
		Set<String> types = new HashSet<String>(bbindexByType.keySet());
		for (String type : types) {
			ByteBufferList oldindex = getIndex(type);
			ByteBufferList newindex = new ByteBufferList(rowLenght); 
			for(ByteBuffer bb : oldindex) {
				((Buffer)bb).rewind();
				if(bb.get() != (byte) -128) {
					newindex.add(bb);
				}
			}
			oldindex.close();
			bbindexByType.put(type, newindex);
		}
	}

	@Override
	public Iterator<DiffMapIndexRow> rowsIterator() {
		
		List<Iterator<? extends ByteBuffer>> bbiterators = new ArrayList<>();
		
		int typei = 0;
		int sizeCum = 0;
		final Object[][] typeToSizeCummulative = new Object[bbindexByType.size()][2];
		for(Entry<String, ByteBufferList> bblist : bbindexByType.entrySet()) {
			bblist.getValue().size();
			bbiterators.add(bblist.getValue().iterator());
			sizeCum += bblist.getValue().size();
			typeToSizeCummulative[typei++] = new Object[]{bblist.getKey(), sizeCum};
		}
		
		final Iterator<ByteBuffer> inxIter = new IteratorChain<ByteBuffer>(bbiterators);
		
		return new Iterator<DiffMapIndex.DiffMapIndexRow>() {
			private int counter = 0;
			
			@Override
			public boolean hasNext() {
				return inxIter.hasNext();
			}

			@Override
			public DiffMapIndexRow next() {
				String type = null;
				for(int i = typeToSizeCummulative.length - 1; i >= 0; i-- ) {
					if(counter < (Integer) typeToSizeCummulative[i][1]) {
						type = (String) typeToSizeCummulative[i][0];
					}
				}
				ByteBuffer bb = inxIter.next();
				IdParts decode = ByteUtils.decode(bb, type);
				
				DiffMapIndexRow row = new DiffMapIndexRow();
				
				row.key = ByteUtils.joinToId(decode);
				((Buffer)bb).position(1);
				row.hash = bb.getInt();
				row.timestamp = new DateTime(bb.getLong());
				
				counter ++;
				
				return row;
			}
			
		};
	}

}
