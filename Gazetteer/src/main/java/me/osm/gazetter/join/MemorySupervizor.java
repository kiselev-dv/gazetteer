package me.osm.gazetter.join;

public class MemorySupervizor {
	
	private static final float treshold = 0.05f;
	
	public static final class InsufficientMemoryException extends Exception {
		
	}

	public static synchronized void checkMemory() throws InsufficientMemoryException {
		Runtime runtime = Runtime.getRuntime();
		
		long maxMemory = runtime.maxMemory();
		long freeMemory = runtime.freeMemory();
		
		if(freeMemory < maxMemory * treshold) {
			runtime.gc();
			freeMemory = runtime.freeMemory();
			
			if(freeMemory < maxMemory * treshold) {
				throw new InsufficientMemoryException();
			}
		}
	}

}
