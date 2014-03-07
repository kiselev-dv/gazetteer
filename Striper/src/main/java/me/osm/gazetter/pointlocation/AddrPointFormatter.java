package me.osm.gazetter.pointlocation;

import java.util.List;

import me.osm.gazetter.addresses.AddressesParser;
import me.osm.gazetter.pointlocation.PLTask.JointHandler;

import org.json.JSONArray;
import org.json.JSONObject;

public class AddrPointFormatter implements JointHandler {

	private static final AddressesParser parser = new AddressesParser(); 

	@Override
	public void handle(JSONObject addrPoint, List<JSONObject> boundaries) {
		
		JSONArray addresses = parser.parse(addrPoint, boundaries);
		
		addrPoint.put("addresses", addresses);
		
		JSONWriter.get().write(addrPoint);
	}
	
}
