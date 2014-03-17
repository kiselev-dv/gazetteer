package me.osm.gazetter.pointlocation;

import java.util.List;

import me.osm.gazetter.addresses.AddressesParser;
import me.osm.gazetter.striper.JSONFeature;

import org.json.JSONArray;
import org.json.JSONObject;

public class AddrPointFormatter implements AddrJointHandler {

	private static final AddressesParser parser = new AddressesParser(); 

	@Override
	public JSONObject handle(JSONObject addrPoint, List<JSONObject> boundaries, 
			List<JSONObject> nearbyStreets,
			JSONObject nearestPlace, 
			JSONObject nearesNeighbour) {
		
		List<JSONObject> streetsRefers = JSONFeature.asRefers(nearbyStreets);
		JSONArray addresses = parser.parse(addrPoint, boundaries, streetsRefers);
		
		addrPoint.put("addresses", addresses);
		
		if(nearestPlace != null) {
			addrPoint.put("nearestCity", JSONFeature.asRefer(nearestPlace));
		}
		
		if(nearesNeighbour != null) {
			addrPoint.put("nearestNeighbour", JSONFeature.asRefer(nearesNeighbour));
		}
		
		if(nearbyStreets != null) {
			addrPoint.put("nearbyStreets", new JSONArray(streetsRefers));
		}
		
		return addrPoint;
	}

	
}
