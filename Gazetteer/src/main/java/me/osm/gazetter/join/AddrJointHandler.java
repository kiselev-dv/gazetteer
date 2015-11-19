package me.osm.gazetter.join;

import java.util.List;

import org.json.JSONObject;


/**
 * Adapter for merged object
 * 
 * @author dkiselev
 */
public interface AddrJointHandler {
	
	/**
	 * @param addrPoint subject
	 * @param polygons which covers subject addrPoint
	 * @param nearbyStreets streets in some radius
	 * @param nearestPlace nearest node with place=city|town|etc tags
	 * @param nearesNeighbour nearest node with place=neighbourhood|quarter tags
 	 * @param associatedStreet street linked via associated street relation
	 * @return merged object
	 */
	public JSONObject handle(
			JSONObject addrPoint, 
			List<JSONObject> polygons, 
			List<JSONObject> nearbyStreets,
			JSONObject nearestPlace, 
			JSONObject nearesNeighbour,
			JSONObject associatedStreet
	);
	
}