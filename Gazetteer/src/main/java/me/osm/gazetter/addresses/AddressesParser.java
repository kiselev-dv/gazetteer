package me.osm.gazetter.addresses;

import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Parse addresses array from object tags, nearby objects and
 * polygonal boundaries.
 * 
 * @returns JSONArray of JSONObject's with addresses.
 * */
public interface AddressesParser {

	public JSONArray parse(JSONObject addrPoint,
			List<JSONObject> boundaries, 
			List<JSONObject> nearbyStreets, 
			JSONObject nearestPlace, 
			JSONObject nearestNeighbour, 
			JSONObject associatedStreet);

	public abstract JSONObject boundariesAsArray(JSONObject jsonObject, List<JSONObject> input);

	public abstract String getAddrLevel(JSONObject obj);

}