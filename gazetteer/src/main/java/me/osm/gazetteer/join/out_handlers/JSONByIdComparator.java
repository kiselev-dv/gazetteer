package me.osm.gazetteer.join.out_handlers;

import java.util.Comparator;

import me.osm.gazetteer.striper.GeoJsonWriter;

/**
 * By Id sorter
 * */
public class JSONByIdComparator implements Comparator<String> {

	private boolean isort;

	/**
	 * @param isort inverse order
	 */
	public JSONByIdComparator(boolean isort) {
		this.isort = isort;
	}

	@Override
	public int compare(String o1, String o2) {

		if(o1 == null && o2 == null) return 0;
    	if(o1 == null || o2 == null) return o1 == null ? -1 : 1;

    	String uid1 = GeoJsonWriter.getId(o1);
    	String uid2 = GeoJsonWriter.getId(o2);

    	if(uid1 == null && uid2 == null) return 0;
    	if(uid1 == null || uid2 == null) return uid1 == null ? -1 : 1;

		return isort ? uid2.compareTo(uid1) : uid1.compareTo(uid2);
	}

}
