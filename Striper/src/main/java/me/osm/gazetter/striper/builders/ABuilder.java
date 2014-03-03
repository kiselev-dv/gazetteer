package me.osm.gazetter.striper.builders;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;


public abstract class ABuilder implements Builder {
	
	public static List<ByteBuffer> findAll(List<ByteBuffer> collection, int index,
			long id, int idFieldOffset) {
		
		List<ByteBuffer> result = new ArrayList<ByteBuffer>();
		
		if(index >= 0 ) {
			result.add(collection.get(index));
			for(int i = 1; ;i++) {

				boolean lp = false;
				boolean ln = false;
				
				ByteBuffer lineP = getSafe(collection, index + i);
				if(lineP != null && lineP.getLong(idFieldOffset) == id) {
					result.add(lineP);
					lp = true;
				}

				ByteBuffer lineN = getSafe(collection, index - i);
				if(lineN != null && lineN.getLong(idFieldOffset) == id) {
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
	public void firstRunDoneWays() {
		//override me if you need
	}

	@Override
	public void firstRunDoneRelations() {
		//override me if you need
	}

	@Override
	public void beforeLastRun() {
		//override me if you need
	}

	@Override
	public void afterLastRun() {
		//override me if you need
	}

}
