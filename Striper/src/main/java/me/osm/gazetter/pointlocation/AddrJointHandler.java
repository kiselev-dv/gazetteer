package me.osm.gazetter.pointlocation;

import java.util.List;

import org.json.JSONObject;

public interface AddrJointHandler {
	public JSONObject handle(
			JSONObject addrPoint, 
			List<JSONObject> polygons, 
			List<JSONObject> nearbyStreets,
			JSONObject nearestPlace, 
			JSONObject nearesNeighbour
	);
}