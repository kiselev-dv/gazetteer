package me.osm.gazetter.pointlocation;

import java.util.List;

import me.osm.gazetter.addresses.AddressesParser;

import org.json.JSONArray;
import org.json.JSONObject;

public class AddrPointFormatter implements AddrJointHandler {

	private static final AddressesParser parser = new AddressesParser(); 

	@Override
	public JSONObject handle(JSONObject addrPoint, List<JSONObject> boundaries, 
			JSONObject nearestPlace, JSONObject nearesNeighbour) {
		
		JSONArray addresses = parser.parse(addrPoint, boundaries);
		
		addrPoint.put("addresses", addresses);
		
		if(nearestPlace != null) {
			addrPoint.put("nearestCity", nearestPlace);
		}
		
		if(nearesNeighbour != null) {
			addrPoint.put("nearestNeighbour", nearesNeighbour);
		}
		
		return addrPoint;
	}

	
	
}
