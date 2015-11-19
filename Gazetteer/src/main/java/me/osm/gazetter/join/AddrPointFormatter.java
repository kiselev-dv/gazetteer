package me.osm.gazetter.join;

import java.util.List;

import me.osm.gazetter.Options;
import me.osm.gazetter.addresses.AddressesParser;
import me.osm.gazetter.striper.JSONFeature;

import org.json.JSONArray;
import org.json.JSONObject;

public class AddrPointFormatter implements AddrJointHandler {
	
	private AddressesParser parser; 

	public AddrPointFormatter() {
		parser = Options.get().getAddressesParser();
	}

	@Override
	public JSONObject handle(JSONObject addrPoint, List<JSONObject> boundaries, 
			List<JSONObject> nearbyStreets,
			JSONObject nearestPlace, 
			JSONObject nearestNeighbour,
			JSONObject associatedStreet) {
		
//		List<JSONObject> streetsRefers = JSONFeature.asRefers(nearbyStreets);
		List<JSONObject> streetsRefers = nearbyStreets;
		
		JSONArray addresses = parser.parse(
				addrPoint, boundaries, streetsRefers, 
				nearestPlace, nearestNeighbour, associatedStreet);
		
		addrPoint.put("addresses", addresses);
		
		if(nearestPlace != null) {
			addrPoint.put("nearestCity", JSONFeature.asRefer(nearestPlace));

			JSONArray neighbourCities = nearestPlace.getJSONArray("neighbourCities");
			if(neighbourCities != null) {
				addrPoint.put("neighbourCities", new JSONArray(JSONFeature.asRefers(neighbourCities)));
			}
		}
		
		
		if(nearestNeighbour != null) {
			addrPoint.put("nearestNeighbour", JSONFeature.asRefer(nearestNeighbour));
		}
		
		if(nearbyStreets != null) {
			addrPoint.put("nearbyStreets", new JSONArray(JSONFeature.asRefers(nearbyStreets)));
		}
		
		return addrPoint;
	}
	
}
