package me.osm.gazetter.addresses;

import java.util.List;

import org.json.JSONObject;

public interface AddressesParser {
	public List<Address> parse(JSONObject addrPoint, List<JSONObject> boundaries);
}
