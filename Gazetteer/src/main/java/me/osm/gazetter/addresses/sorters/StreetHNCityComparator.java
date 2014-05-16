package me.osm.gazetter.addresses.sorters;

import java.util.HashMap;
import java.util.Map;

import me.osm.gazetter.addresses.AddrLevelsComparator;
import me.osm.gazetter.addresses.AddressesLevelsMatcher;
import me.osm.gazetter.addresses.Constants;

import org.apache.commons.lang3.ObjectUtils;
import org.json.JSONObject;

/**
 * Sorts addresses parts in order: Street, House, City, Country. 
 * */
public class StreetHNCityComparator implements AddrLevelsComparator {

	@Override
	public int compare(JSONObject o1, JSONObject o2) {
		Integer i1 = order.get(o1.getString(AddressesLevelsMatcher.ADDR_LVL));
		Integer i2 = order.get(o2.getString(AddressesLevelsMatcher.ADDR_LVL));
		return ObjectUtils.compare(i1, i2);
	}

	private static final Map<String, Integer> order = new HashMap<>();
	private static final Map<String, Integer> type2size = Constants.defaultType2size;
	
	static {
		int i = 1;
		order.put("postcode", i++);

		order.put("street", i++);
		order.put("hn", i++);
		order.put("letter", i++);
		
		order.put("place:quarter", i++);
		order.put("place:neighbourhood", i++);
		order.put("place:suburb", i++);
		order.put("place:allotments", i++);
		order.put("place:locality", i);
		order.put("place:isolated_dwelling", i);
		order.put("place:village", i);
		order.put("place:hamlet", i);
		order.put("place:town", i);
		order.put("place:city", i);
		i++;

		order.put("boundary:8", i++);
		order.put("boundary:6", i++);
		order.put("boundary:5", i++);
		order.put("boundary:4", i++);
		order.put("boundary:3", i++);
		order.put("boundary:2", i++);

	}
	
	@Override
	public int getLVLSize(String lelvel) {
		return type2size.get(lelvel);
	}
	
	@Override
	public boolean supports(String lelvel) {
		return order.containsKey(lelvel);
	}

}
