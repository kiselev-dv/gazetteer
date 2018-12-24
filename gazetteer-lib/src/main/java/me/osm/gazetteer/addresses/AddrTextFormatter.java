package me.osm.gazetteer.addresses;

import java.util.List;

import org.json.JSONObject;

/**
 * Generate full address string from addr levels
 * */
public interface AddrTextFormatter {

	/**
	 * Join part objects names and attrs into address string
	 *
	 * @param addrJsonRow
	 * 				Sorted parts of address
	 * @param properties
	 * 				Properties of base object
	 * @param lang
	 * 				Language
	 *
	 * @return address as string
	 * */
	String joinNames(List<JSONObject> addrJsonRow, JSONObject properties, String lang);

	/**
	 * Join admin boundaries names
	 *
	 * @param result
	 * 			Sorted boundaries
	 * @param lang
	 * 			Language
	 *
	 * @return Address as string
	 * */
	String joinBoundariesNames(List<JSONObject> result, String lang);

}
