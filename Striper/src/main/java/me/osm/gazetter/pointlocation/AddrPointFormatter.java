package me.osm.gazetter.pointlocation;

import java.util.List;

import me.osm.gazetter.addresses.AddressesParser;
import me.osm.gazetter.striper.GeoJsonWriter;

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
			addrPoint.put("nearestCity", asRefer(nearestPlace));
		}
		
		if(nearesNeighbour != null) {
			addrPoint.put("nearestNeighbour", asRefer(nearesNeighbour));
		}
		
		return addrPoint;
	}

	private JSONObject asRefer(JSONObject nearestPlace) {
		JSONObject result = new JSONObject();
		result.put("id", nearestPlace.getString("id"));
		result.put(GeoJsonWriter.PROPERTIES, nearestPlace.getJSONObject(GeoJsonWriter.PROPERTIES));
		return result;
	}

	
	
}
