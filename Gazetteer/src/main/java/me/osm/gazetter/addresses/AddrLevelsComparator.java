package me.osm.gazetter.addresses;

import java.util.Comparator;

import org.json.JSONObject;

/**
 * Compares addresses parts and sorts them accordingly local traditions.
 * Eg. City, Street, House or House, Street, City.
 * */
public interface AddrLevelsComparator extends Comparator<JSONObject> {
	
	public int getLVLSize(String lelvel);
	public boolean supports(String lelvel);
	
}
