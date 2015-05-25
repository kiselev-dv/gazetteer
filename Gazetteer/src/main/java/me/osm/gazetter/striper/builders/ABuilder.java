package me.osm.gazetter.striper.builders;

import gnu.trove.list.TLongList;


public abstract class ABuilder implements Builder {
	
	@Override
	public void secondRunDoneWays() {
		//override me if you need
	}
	
	@Override
	public void firstRunDoneNodes() {
		//override me if you need
	}
	
	@Override
	public void firstRunDoneWays() {
		//override me if you need
	}

	@Override
	public void firstRunDoneRelations() {
		//override me if you need
	}
	
//	public static final List<ByteBuffer> findAll(List<ByteBuffer> collection, int index,
//			long id, int idFieldOffset) {
//		
//		List<ByteBuffer> result = new ArrayList<ByteBuffer>();
//		
//		if(index >= 0 ) {
//			result.add(collection.get(index));
//			for(int i = 1; ;i++) {
//
//				boolean lp = false;
//				boolean ln = false;
//				
//				ByteBuffer lineP = getSafe(collection, index + i);
//				if(lineP != null && lineP.getLong(idFieldOffset) == id) {
//					result.add(lineP);
//					lp = true;
//				}
//
//				ByteBuffer lineN = getSafe(collection, index - i);
//				if(lineN != null && lineN.getLong(idFieldOffset) == id) {
//					result.add(lineN);
//					ln = true;
//				}
//				
//				if(!lp && !ln) {
//					break;
//				}
//				
//			}
//		}
//		
//		return result;
//	}
	
	public static final int binarySearchWithMask(TLongList list, long key) {
		int imin = 0;
		int imax = list.size() - 1;
		while (imax >= imin) {
			int imid = imin + (imax - imin) / 2;
			long guess = list.get(imid) >> 16;
			if (guess == key) {
				return imid;
			}
			else if (guess < key) {
				imin = imid + 1;
			}
			else {
				imax = imid - 1;
			}
		}

		return -1;
	}

//	private static ByteBuffer getSafe(List<ByteBuffer> collection, int i) {
//		if(i >= 0 && i < collection.size()) {
//			return collection.get(i);
//		}
//		return null;
//	}
	
	public String getThreadPoolUser() {
		return this.getClass().getName();
	}
	
}
