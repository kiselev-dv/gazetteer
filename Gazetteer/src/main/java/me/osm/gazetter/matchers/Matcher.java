package me.osm.gazetter.matchers;

import java.util.Collection;

public class Matcher {

	public static boolean isPlaceNameMatch(String name, Collection<String> names){
		return names.contains(name);
	}
	
}
