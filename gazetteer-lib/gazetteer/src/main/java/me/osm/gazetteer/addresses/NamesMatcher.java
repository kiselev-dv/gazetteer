package me.osm.gazetteer.addresses;

import java.util.Map;
import java.util.Set;

/**
 * Names matching.
 * */
public interface NamesMatcher {

	/**
	 * Does name matches to names from <code>names</code> for cities
	 *
	 * @param name subject name
	 * @param names other objects names
	 *
	 * @return matches
	 * */
	public boolean isPlaceNameMatch(String name,
			Set<String> names);

	/**
	 * Does name matches to names from <code>names</code> for cities
	 *
	 * @param name subject name
	 * @param filterNameTags other objects names with tags keys
	 *
	 * @return matches
	 * */
	public boolean isPlaceNameMatch(String name,
			Map<String, String> filterNameTags);

	/**
	 * Does addr:street matches to street object
	 *
	 * @param street street name
	 * @param filterNameTags other streets names with keys
	 *
	 * @return matches
	 * */
	public boolean isStreetNameMatch(String street,
			Map<String, String> filterNameTags);

	/**
	 * Looking for matched streets inside highways networks.
	 *
	 * @param o1names names and keys of first object
	 * @param o2names names and keys of second object
	 *
	 * @return matches
	 * */
	public boolean doesStreetsMatch(Map<String, String> o1names,
			Map<String, String> o2names);


}
