package me.osm.gazetter.addresses;

import java.util.Comparator;

import org.json.JSONObject;

/**
 * Compares addresses parts and sorts them accordingly local traditions.
 * Eg. City, Street, House or House, Street, City.
 * */
public interface AddrLevelsComparator extends Comparator<JSONObject> {
	
	/**
	 * Numeric value of address level "importance"
	 * 
	 * @param lelvel
	 * 			Address part (level) name
	 * @return address part level size
	 * */
	public int getLVLSize(String lelvel);
	
	/**
	 * Does this comparator recognize this level 
	 * 
	 * @param lelvel
	 * 			Address part (level) name
	 * @return supports
	 * */
	public boolean supports(String lelvel);
	
}
