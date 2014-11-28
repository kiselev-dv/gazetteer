package me.osm.gazetter.join.out_handlers;

import org.json.JSONObject;

public class JsonJoinOutH extends AddressPerRowJOHBase {

	public static final String NAME = "out-json";

	@Override
	protected void handle(JSONObject object, JSONObject address, String stripe) {
		String optString = object.optString("ftype");
		System.out.println(optString);
	}

}
