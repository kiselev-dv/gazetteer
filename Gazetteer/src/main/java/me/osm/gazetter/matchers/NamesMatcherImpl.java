package me.osm.gazetter.matchers;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class NamesMatcherImpl implements NamesMatcher {

	@Override
	public boolean isPlaceNameMatch(String name, Set<String> names){
		return names.contains(name);
	}

	@Override
	public boolean isStreetNameMatch(String street,
			Map<String, String> filterNameTags) {
		
		return new HashSet<>(filterNameTags.values()).contains(street); 
	}

	@Override
	public boolean isPlaceNameMatch(String name,
			Map<String, String> filterNameTags) {

		return new HashSet<>(filterNameTags.values()).contains(name); 
	}
	
}
