package me.osm.gazetter.join.out_handlers;

import java.util.Comparator;

import me.osm.gazetter.striper.GeoJsonWriter;

public class JSONByIdComparator implements Comparator<String> {

	@Override
	public int compare(String o1, String o2) {
		
		if(o1 == null && o2 == null) return 0;
    	if(o1 == null || o2 == null) return o1 == null ? -1 : 1;
    	
    	String uid1 = GeoJsonWriter.getId(o1);
    	String uid2 = GeoJsonWriter.getId(o2);
    	
		return uid1.compareTo(uid2);
	}

}
