package me.osm.gazetter;

import me.osm.gazetter.addresses.AddrLevelsSorting;

public class Options {
	
	public static final class SecondaryOptionsInitializationException
		extends RuntimeException {

		private static final long serialVersionUID = -2739610432163207207L;
		
	}
	
	private static volatile Options instance;
	private AddrLevelsSorting sorting;

	private Options() {
		
	}

	public static void initialize(AddrLevelsSorting sorting) {
		if(instance != null) {
			throw new SecondaryOptionsInitializationException();
		}
		
		instance = new Options();
		instance.sorting = sorting;
	}
	
	public static Options get() {
		return instance;
	}

	public AddrLevelsSorting getSorting() {
		return sorting;
	}
	
}
