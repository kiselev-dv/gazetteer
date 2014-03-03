package me.osm.gazetter.addresses;

import org.json.JSONObject;

public interface Address {
	public JSONObject asLinks();
	public String asFullText();
	public String asFullText(String lang);
}
