package me.osm.gazetteer.addresses;

/**
 * Supported addr levels orders.
 * */
public enum AddrLevelsSorting {

	/**
	 * House, Street, City, Country
	 * */
	HN_STREET_CITY,

	/**
	 * Street, House, City, Country
	 * */
	STREET_HN_CITY,

	/**
	 * Country, City, Street, House
	 * */
	CITY_STREET_HN
}
