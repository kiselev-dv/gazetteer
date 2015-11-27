package me.osm.gazetter.join.out_handlers;

/**
 * Type of sort
 * 
 * @See GazetteerOutWriter
 * */
public enum OutGazetteerSort {
	
	/**
	 * Skip sorting
	 * */
	NONE, 
	
	/**
	 * Sort by id (type-ghash-tail)
	 * */
	ID, 

	/**
	 * Sort with dependencies
	 * */
	HIERARCHICAL, 
	
	/**
	 * Do not sort results, but skip duplicates 
	 * */
	UNIQUE
}
