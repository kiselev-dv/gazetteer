package me.osm.gazetter.addresses.parsers;

import java.util.List;

import me.osm.gazetter.addresses.AddrElementsOrder;

import org.json.JSONObject;

public class ByTerritoryAddress extends AddressBase {


	public ByTerritoryAddress(AddrElementsOrder order,
			JSONObject addrPoint, List<JSONObject> boundaries) {
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
		return hn + DELIMITTER + boundaries;
	}

	@Override
	public String asFullText(String lang) {
		return null;
	}
	
}