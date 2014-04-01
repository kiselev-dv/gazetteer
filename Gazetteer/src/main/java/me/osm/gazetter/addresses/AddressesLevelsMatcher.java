package me.osm.gazetter.addresses;

import java.util.List;
import java.util.Map;

import org.json.JSONObject;

public interface AddressesLevelsMatcher {
	
	public static final String ADDR_NAMES = "names";

	public static final String ADDR_NAME = "name";

	public static final String ADDR_LVL = "lvl";

	public static final String ADDR_LVL_SIZE = "lvl-size";

	public JSONObject hnAsJSON(JSONObject addrPoint, JSONObject addrRow);

	public JSONObject streetAsJSON(JSONObject addrPoint, JSONObject addrRow,
			JSONObject associatedStreet, List<JSONObject> nearbyStreets);

	public JSONObject quarterAsJSON(JSONObject addrPoint, JSONObject addrRow,
			Map<String, JSONObject> level2Boundary, JSONObject nearestNeighbour);

	public JSONObject letterAsJSON(JSONObject addrPoint, JSONObject addrRow);

	public JSONObject cityAsJSON(JSONObject addrPoint, JSONObject addrRow,
			Map<String, JSONObject> level2Boundary, JSONObject nearestPlace);

	public JSONObject postCodeAsJSON(JSONObject addrPoint, JSONObject addrRow);

}
