package me.osm.gazetter.addresses;

import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;

public class StreetHNCityComparator implements AddrLevelsComparator {

	@Override
	public int compare(JSONObject o1, JSONObject o2) {
		int i1 = order.get(o1.getString(AddressesParser.ADDR_LVL));
		int i2 = order.get(o2.getString(AddressesParser.ADDR_LVL));
		return i1 - i2;
	}

	private static final Map<String, Integer> order = new HashMap<>();
	private static final Map<String, Integer> type2size = Constants.defaultType2size;
	
	static {
		int i = 1;
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
