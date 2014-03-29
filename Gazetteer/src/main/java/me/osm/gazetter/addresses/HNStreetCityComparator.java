package me.osm.gazetter.addresses;

import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;

class HNStreetCityComparator implements AddrLevelsComparator {
	
	@Override
	public int compare(JSONObject o1, JSONObject o2) {
		int i1 = order.get(o1.getString(AddressesParser.ADDR_LVL));
		int i2 = order.get(o2.getString(AddressesParser.ADDR_LVL));
		return i1 - i2;
	}
	
	private static final Map<String, Integer> order = new HashMap<>();
	private static final Map<String, Integer> type2size = new HashMap<>();
	
	static {
		order.put("hn", 10);
		order.put("letter", 12);
		order.put("street", 20);
		order.put("place:quarter", 30);
		order.put("place:neighbourhood", 40);
		order.put("place:suburb", 50);
		order.put("place:allotments", 60);
		order.put("place:locality", 70);
		order.put("place:isolated_dwelling", 70);
		order.put("place:village", 70);
		order.put("place:hamlet", 70);
		order.put("place:town", 70);
		order.put("place:city", 70);

		order.put("boundary:8", 80);
		order.put("boundary:6", 90);
		order.put("boundary:5", 100);
		order.put("boundary:4", 110);
		order.put("boundary:3", 120);
		order.put("boundary:2", 130);
		
		type2size.putAll(order);
		order.put("letter", 8);
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