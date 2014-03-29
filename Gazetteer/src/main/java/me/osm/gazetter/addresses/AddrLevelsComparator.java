package me.osm.gazetter.addresses;

import java.util.Comparator;

import org.json.JSONObject;

public interface AddrLevelsComparator extends Comparator<JSONObject> {
	
	public int getLVLSize(String lelvel);
	public boolean supports(String lelvel);
	
}
