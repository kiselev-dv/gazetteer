package me.osm.gazetter.pointlocation;

import java.util.List;

import me.osm.gazetter.addresses.AddrElementsOrder;
import me.osm.gazetter.addresses.Address;
import me.osm.gazetter.addresses.AddressesParserFacade;
import me.osm.gazetter.addresses.AddressesParsersFactory;
import me.osm.gazetter.pointlocation.PLTask.JointHandler;

import org.json.JSONArray;
import org.json.JSONObject;

public class AddrPointFormatter implements JointHandler {

	private AddressesParserFacade parsersFacade = 
			new AddressesParserFacade(new AddressesParsersFactory(AddrElementsOrder.BIG_TO_SMALL));

	@Override
	public void handle(JSONObject addrPoint, List<JSONObject> boundaries) {
		
		List<Address> addresses = parsersFacade.parse(addrPoint, boundaries);
		JSONArray addrJson = new JSONArray();
		
		for(Address a : addresses) {
			addrJson.put(a.asFullText());
		}
		
		addrPoint.put("addresses", addrJson);
		
		AddrPointWriter.get().write(addrPoint);
	}
	
}
