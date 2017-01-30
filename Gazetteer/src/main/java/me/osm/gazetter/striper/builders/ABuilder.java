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

	public String getThreadPoolUser() {
		return this.getClass().getName();
	}
	
}
