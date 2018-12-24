package me.osm.gazetteer.ExternalSorting;

/**
 * Allows to reduce lines which where matched
 * as equals by Comparator while sort
 * */
public interface Reducer {

	/**
	 * @param lastLine
	 * 				line A
	 * @param r
	 * 				line B
	 * @return merged string
	 * */
	public String merge(String lastLine, String r);

}
