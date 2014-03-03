package me.osm.gazetter.addresses.parsers;

import java.util.ArrayList;
import java.util.List;

import me.osm.gazetter.addresses.AddrElementsOrder;
import me.osm.gazetter.addresses.Address;
import me.osm.gazetter.addresses.AddressesParser;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

public class RegularParser implements AddressesParser {
	
	private AddrElementsOrder order;

	public RegularParser(AddrElementsOrder order) {
		this.order = order;
	}

	@Override
	public List<Address> parse(JSONObject addrPoint, List<JSONObject> boundaries) {
		
		List<Address> result = new ArrayList<>();
		
		String street = addrPoint.getJSONObject("properties").optString("addr:street");
		
		if(StringUtils.isEmpty(street)) {
			result.add(new ByTerritoryAddress(order, addrPoint, boundaries));
		}
		else {
			result.add(new ByTerritoryAddress(order, addrPoint, boundaries));
		}
		
		return result;
	}

}
