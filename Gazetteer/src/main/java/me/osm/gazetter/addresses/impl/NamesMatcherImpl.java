package me.osm.gazetter.addresses.impl;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import me.osm.gazetter.addresses.NamesMatcher;

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
