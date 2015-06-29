package me.osm.gazetter.join.out_handlers;

import java.util.Comparator;

import me.osm.gazetter.striper.GeoJsonWriter;

public class JSONHComparator implements Comparator<String> {

	@Override
	public int compare(String o1, String o2) {
		
		if(o1 == null && o2 == null) return 0;
    	if(o1 == null || o2 == null) return o1 == null ? -1 : 1;
    	
    	String h1 = GeoJsonWriter.getHHash(o1);
    	String h2 = GeoJsonWriter.getHHash(o2);
    	
    	if(h1 == null && h2 == null) return 0;
    	if(h1 == null || h2 == null) return h1 == null ? -1 : 1;
    	
		return h1.compareTo(h2);
	}

}
