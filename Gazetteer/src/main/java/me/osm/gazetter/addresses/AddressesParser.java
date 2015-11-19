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

	/**
	 * Represent address as JSONArray of address parts
	 * 
	 * @param addrPoint
	 * 			Addr point (base object)
	 * @param boundaries
	 * 			Boundaries covers this point
	 * @param nearbyStreets
	 * 			Streets inside some radius
	 * @param nearestPlace
	 * 			Nearest place=city|town|etc node
	 * @param nearestNeighbour
	 * 			Nearest place=quarter|neighbourhood node
	 * @param associatedStreet
	 * 			Street linked via associated street relation
	 * 
	 * @return address
	 * */
	public JSONArray parse(JSONObject addrPoint,
			List<JSONObject> boundaries, 
			List<JSONObject> nearbyStreets, 
			JSONObject nearestPlace, 
			JSONObject nearestNeighbour, 
			JSONObject associatedStreet);

	/**
	 * Join all boundaries into one address json oject
	 * 
	 * @param jsonObject
	 * 			Subject
	 * @param input
	 * 			Upper boundaries (enclosing boundaries)
	 * 
	 * @return encoded boundaries address
	 * */
	public abstract JSONObject boundariesAsArray(JSONObject jsonObject, List<JSONObject> input);

	/**
	 * Address level of boundary or object
	 * 
	 * @param obj subject
	 * @return Level (part) name
	 * */
	public abstract String getAddrLevel(JSONObject obj);

}