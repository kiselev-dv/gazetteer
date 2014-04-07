package me.osm.gazetter.addresses;

import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

public interface AddressesParser {

	public JSONArray parse(JSONObject addrPoint,
			List<JSONObject> boundaries, 
			List<JSONObject> nearbyStreets, 
			JSONObject nearestPlace, 
			JSONObject nearestNeighbour, 
			JSONObject associatedStreet);

	public abstract JSONObject boundariesAsArray(List<JSONObject> input);

	public abstract String getAddrLevel(JSONObject obj);

}