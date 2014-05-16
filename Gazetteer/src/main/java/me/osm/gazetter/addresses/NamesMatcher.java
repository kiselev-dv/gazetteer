package me.osm.gazetter.addresses;

import java.util.Map;
import java.util.Set;

/**
 * Names matching.
 * */
public interface NamesMatcher {

	public boolean isPlaceNameMatch(String name,
			Set<String> names);

	public boolean isStreetNameMatch(String street,
			Map<String, String> filterNameTags);

	public boolean isPlaceNameMatch(String name,
			Map<String, String> filterNameTags);

}