package me.osm.gazetter.addresses;

import java.util.Map;
import java.util.Set;

import org.json.JSONObject;

/**
 * Names matching.
 * */
public interface NamesMatcher {

	public boolean isPlaceNameMatch(String name,
			Set<String> names);

	public boolean isPlaceNameMatch(String name,
			Map<String, String> filterNameTags);

	/**
	 * Does addr:street matches to street object
	 * */
	public boolean isStreetNameMatch(String street,
			Map<String, String> filterNameTags);
	
	/**
	 * Looking for matched streets inside highways networks.
	 * */
	public boolean doesStreetsMatch(Map<String, String> o1names, 
			Map<String, String> o2names);
	
	
}