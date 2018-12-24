package me.osm.gazetteer.join.out_handlers;

/**
 * {@link GazetteerOutWriter} result JSON propertiers names
 * */
public class GazetteerSchemeConstants {

	/**
	 * MD5 hash.
	 *
	 * Used for diff generation
	 * */
	public static final String GAZETTEER_SCHEME_MD5 = "md5";

	/**
	 * Full geometry of object
	 * */
	public static final String GAZETTEER_SCHEME_FULL_GEOMETRY = "full_geometry";

	/**
	 * Object centroid
	 * */
	public static final String GAZETTEER_SCHEME_CENTER_POINT = "center_point";

	/**
	 * Nearby address of POI
	 * */
	public static final String GAZETTEER_SCHEME_NEARBY_ADDRESSES = "nearby_addresses";

	/**
	 * POIs' keywords
	 * */
	public static final String GAZETTEER_SCHEME_POI_KEYWORDS = "poi_keywords";

	/**
	 * Type (class) of the POI
	 * */
	public static final String GAZETTEER_SCHEME_POI_CLASS = "poi_class";

	/**
	 * Original object TAGs
	 * */
	public static final String GAZETTEER_SCHEME_TAGS = "tags";

	/**
	 * Level of addr part
	 * */
	public static final String GAZETTEER_SCHEME_ADDR_LEVEL = "addr_level";

	/**
	 * References to address parts
	 * */
	public static final String GAZETTEER_SCHEME_REFS = "refs";

	/**
	 * Create (write) timestamp
	 * */
	public static final String GAZETTEER_SCHEME_TIMESTAMP = "timestamp";

	/**
	 * Feature type
	 * */
	public static final String GAZETTEER_SCHEME_TYPE = "type";

	/**
	 * Feature ID {@link AddressPerRowJOHBase}
	 * */
	public static final String GAZETTEER_SCHEME_FEATURE_ID = "feature_id";

	/**
	 * Row ID
	 * */
	public static final String GAZETTEER_SCHEME_ID = "id";

	/**
	 * Array of streets nearby
	 * */
	public static final String GAZETTEER_SCHEME_NEARBY_STREETS = "nearby_streets";

	/**
	 * Array of Places nearby
	 * */
	public static final String GAZETTEER_SCHEME_NEARBY_PLACES = "nearby_places";

	/**
	 * Nearest place=neighbourhood
	 * */
	public static final String GAZETTEER_SCHEME_NEAREST_NEIGHBOUR = "nearest_neighbour";

	/**
	 * Nearest place=city/town/etc
	 * */
	public static final String GAZETTEER_SCHEME_NEAREST_PLACE = "nearest_place";

	/**
	 * Address object
	 * */
	public static final String GAZETTEER_SCHEME_ADDRESS = "address";

	/**
	 * The way poi was matched to address
	 * */
	public static final String GAZETTEER_SCHEME_POI_ADDR_MATCH = "poi_addr_match";

	/**
	 * Translation of POI class
	 * */
	public static final String GAZETTEER_SCHEME_POI_TYPE_NAMES = "poi_class_translated";

}
