package me.osm.gazetter.join.util;

public class MemorySupervizor {
	
	private static final int MB = 1048576;
	
	public static final class InsufficientMemoryException extends Exception {

		private static final long serialVersionUID = -8081720083932767145L;
		
	}

	public static synchronized void checkMemory() throws InsufficientMemoryException {

		if(getAvaibleRAMMeg() < 250) {
			Runtime.getRuntime().gc();
			
			if(getAvaibleRAMMeg() < 250) {
				throw new InsufficientMemoryException();
			}
		}
	}

	public static long getAvaibleRAMMeg() {
		Runtime runtime = Runtime.getRuntime();
		long used = (runtime.totalMemory() - runtime.freeMemory()) / MB;
		
		return runtime.maxMemory() / MB - used;
	}

}
