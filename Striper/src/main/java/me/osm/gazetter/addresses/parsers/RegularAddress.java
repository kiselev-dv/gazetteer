package me.osm.gazetter.addresses.parsers;

import java.util.List;

import me.osm.gazetter.addresses.AddrElementsOrder;

import org.json.JSONObject;

public class RegularAddress extends AddressBase {

	public RegularAddress(AddrElementsOrder order, JSONObject addrPoint,
			List<JSONObject> boundaries) {
		super(order, addrPoint, boundaries);
	}

	@Override
	public JSONObject asLinks() {
		return null;
	}

	@Override
	public String asFullText() {
		
		String boundaries = joinBndriesText();
		String hn = addrPoint.getJSONObject("properties").optString("addr:housenumber");
		String street = addrPoint.getJSONObject("properties").optString("addr:street");
		return hn + DELIMITTER + street + DELIMITTER + boundaries;
		
	}

	@Override
	public String asFullText(String lang) {
		return null;
	}

}
