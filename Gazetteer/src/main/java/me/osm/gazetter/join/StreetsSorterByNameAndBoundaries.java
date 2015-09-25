package me.osm.gazetter.join;

import java.util.Comparator;

import me.osm.gazetter.addresses.AddressesUtils;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

public final class StreetsSorterByNameAndBoundaries implements
			Comparator<JSONObject> {
		
	public static final StreetsSorterByNameAndBoundaries INSTANCE = new StreetsSorterByNameAndBoundaries();
	
	@Override
	public int compare(JSONObject o1, JSONObject o2) {
		
		String bhash1 = String.valueOf(o1.optInt("boundariesHash")) + 
				AddressesUtils.foldASCII(StringUtils.stripToEmpty(o1.getJSONObject("properties").optString("name"))).toLowerCase();
		
		String bhash2 = String.valueOf(o2.optInt("boundariesHash")) + 
				AddressesUtils.foldASCII(StringUtils.stripToEmpty(o2.getJSONObject("properties").optString("name"))).toLowerCase();
		
		return bhash2.compareTo(bhash1);
	}
		
}