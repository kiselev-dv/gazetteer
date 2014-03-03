package me.osm.gazetter.addresses.parsers;

import java.util.List;

import me.osm.gazetter.addresses.AddrElementsOrder;
import me.osm.gazetter.addresses.Address;
import me.osm.gazetter.addresses.AddressesUtils;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

public abstract class AddressBase implements Address {
	
	protected AddrElementsOrder order;
	protected JSONObject addrPoint;
	protected List<JSONObject> boundaries;
	
	public static final String DELIMITTER = ", ";

	public AddressBase(AddrElementsOrder order,
			JSONObject addrPoint, List<JSONObject> boundaries) {
		this.order = order;
		this.addrPoint = addrPoint;
		this.boundaries = boundaries;
	}
	
	protected String joinBndriesText() {
		StringBuilder sb = new StringBuilder();
		List<JSONObject> places = AddressesUtils.filterPlaces(boundaries);
		List<JSONObject> bndrs = AddressesUtils.filterAdminBoundaries(boundaries);
		
		for(JSONObject place : places) {
			String name = place.getJSONObject("properties").optString("name");
			if(!StringUtils.isEmpty(name)) {
				sb.append(", ").append(name);
			}
		}

		for(JSONObject place : bndrs) {
			String name = place.getJSONObject("properties").optString("name");
			if(!StringUtils.isEmpty(name)) {
				sb.append(", ").append(name);
			}
		}
		
		if(sb.length() > DELIMITTER.length()) {
			return sb.substring(DELIMITTER.length());
		}
		
		return "";
	}
}
