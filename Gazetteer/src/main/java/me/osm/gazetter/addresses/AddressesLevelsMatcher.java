package me.osm.gazetter.addresses;

import java.util.List;
import java.util.Map;

import org.json.JSONObject;

/**
 * Return addr levels as addr part JSON object. 
 * Try to match data from tags with objects. 
 * (Place points, streets, and so on.)
 * <p>
 * If matches found, add alt names and links to the AddrPart object.
 * */
public interface AddressesLevelsMatcher {
	
	/**
	 * JSON property for array of object names
	 * */
	public static final String ADDR_NAMES = "names";

	/**
	 * JSON property for primary name
	 * */
	public static final String ADDR_NAME = "name";

	/**
	 * JSON property for address level (part) name
	 * */
	public static final String ADDR_LVL = "lvl";

	/**
	 * JSON property for address level (part) size
	 * */
	public static final String ADDR_LVL_SIZE = "lvl-size";

	/**
	 * Encode house number as JSON
	 * 
	 * @param addrPoint
	 * 			Address point (source object)
	 * @param addrRow
	 * 			Address row (full address row, with founded 
	 * 			streets and admin boundaries)
	 * 
	 * @return JSONObject with added house number part
	 * */
	public JSONObject hnAsJSON(JSONObject addrPoint, JSONObject addrRow);

	/**
	 * Encode street as JSON
	 * 
	 * @param addrPoint
	 * 			Address point (source object)
	 * @param addrRow
	 * 			Address row (full address row, with founded 
	 * 			streets and admin boundaries)
	 * @param associatedStreet
	 * 			Street, linked via relation
	 * @param nearbyStreets
	 * 			Streets in some radius
	 * @param boundariesHash
	 * 			Hash of upper boundaries (for city districts)
	 * 
	 * @return JSONObject with added street part
	 * */
	public JSONObject streetAsJSON(JSONObject addrPoint, JSONObject addrRow,
			JSONObject associatedStreet, List<JSONObject> nearbyStreets, int boundariesHash);

	/**
	 * Encode city district (quarter)
	 * 
	 * @param addrPoint
	 * 			Address point (source object)
	 * @param addrRow
	 * 			Address row (full address row, with founded 
	 * 			streets and admin boundaries)
	 * @param level2Boundary
	 * 			Boundaries mapped by their levels
	 * @param nearestNeighbour
	 * 			Nearest place=neighbourhood point
	 * 
	 * @return JSONObject with added street part
	 * */
	public JSONObject quarterAsJSON(JSONObject addrPoint, JSONObject addrRow,
			Map<String, JSONObject> level2Boundary, JSONObject nearestNeighbour);

	/**
	 * Encode city district (quarter)
	 * 
	 * @param addrPoint
	 * 			Address point (source object)
	 * @param addrRow
	 * 			Address row (full address row, with founded 
	 * 			streets and admin boundaries)
	 * @param level2Boundary
	 * 			Boundaries mapped by their levels
	 * @param nearestPlace
	 * 			Nearest place=city|town|village|etc point
	 * @param nearestPlaceLvl
	 * 			Level (city|town|village|etc) of nearest place
	 *  
	 * @return JSONObject with added city part
	 * */
	public JSONObject cityAsJSON(JSONObject addrPoint, JSONObject addrRow,
			Map<String, JSONObject> level2Boundary, JSONObject nearestPlace, String nearestPlaceLvl);

	/**
	 * Encode post code (zip code)
	 * 
	 * @param addrPoint
	 * 			Address point (source object)
	 * @param addrRow
	 * 			Address row (full address row, with founded 
	 * 			streets and admin boundaries)
	 *  
	 * @return JSONObject with added street part
	 * */
	public JSONObject postCodeAsJSON(JSONObject addrPoint, JSONObject addrRow);

}
