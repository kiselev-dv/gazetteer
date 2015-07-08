package me.osm.gazetter.addresses;

import java.util.HashMap;
import java.util.Map;

/**
 * Constants, and some JSON fields names.
 * */
public class Constants {
	public static final Map<String, Integer> defaultType2size = new HashMap<>();
	public static final int HN_LVL_SIZE = 10;
	public static final int STREET_LVL_SIZE = 20;
	public static final int POSTCODE_LVL_SIZE = 55;
	
	static {
		defaultType2size.put("letter", 8);
		defaultType2size.put("hn", HN_LVL_SIZE);
		defaultType2size.put("street", STREET_LVL_SIZE);
		
		defaultType2size.put("place:quarter", 30);
		defaultType2size.put("place:neighbourhood", 40);
		defaultType2size.put("place:suburb", 50);
		defaultType2size.put("boundary:10", 51);
		defaultType2size.put("boundary:9", 52);
		
		defaultType2size.put("postcode", POSTCODE_LVL_SIZE);
		
		defaultType2size.put("place:allotments", 60);
		defaultType2size.put("place:locality", 70);
		defaultType2size.put("place:isolated_dwelling", 70);
		defaultType2size.put("place:village", 70);
		defaultType2size.put("place:hamlet", 70);
		defaultType2size.put("place:town", 70);
		defaultType2size.put("place:city", 70);

		defaultType2size.put("boundary:8", 80);
		defaultType2size.put("boundary:7", 85);
		defaultType2size.put("boundary:6", 90);
		defaultType2size.put("boundary:5", 100);
		defaultType2size.put("boundary:4", 110);
		defaultType2size.put("boundary:3", 120);
		defaultType2size.put("boundary:2", 130);
	}
}
