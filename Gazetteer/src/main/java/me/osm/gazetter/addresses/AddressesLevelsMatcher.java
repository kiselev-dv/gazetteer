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
	
	public static final String ADDR_NAMES = "names";

	public static final String ADDR_NAME = "name";

	public static final String ADDR_LVL = "lvl";

	public static final String ADDR_LVL_SIZE = "lvl-size";

	public JSONObject hnAsJSON(JSONObject addrPoint, JSONObject addrRow);

	public JSONObject streetAsJSON(JSONObject addrPoint, JSONObject addrRow,
			JSONObject associatedStreet, List<JSONObject> nearbyStreets, int boundariesHash);

	public JSONObject quarterAsJSON(JSONObject addrPoint, JSONObject addrRow,
			Map<String, JSONObject> level2Boundary, JSONObject nearestNeighbour);

	public JSONObject cityAsJSON(JSONObject addrPoint, JSONObject addrRow,
			Map<String, JSONObject> level2Boundary, JSONObject nearestPlace, String string);

	public JSONObject postCodeAsJSON(JSONObject addrPoint, JSONObject addrRow);

}
