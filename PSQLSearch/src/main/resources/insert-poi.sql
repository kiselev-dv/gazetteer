INSERT into pois (id, type, feature_id, osm_id, osm_type, full_text, 
	name, name_alt, name_opt,
	housenumber_exact, housenumber_array,
	street, street_opt,
	neighbourhood,
	locality, locality_opt, locality_type,
	centroid,
	base_score,
	poi_class,
	keywords,
	created,
	refs,
	json
)
VALUES (
	:id, :type, :feature_id, :osm_id, :osm_type, :full_text, 
	:name::tsvector, name_alt::tsvector, :name_opt,
	:hn_exact, :hn_array,
	:street::tsvector, :street_opt, 
	:neighbourhood::tsvector,
	:locality::tsvector, :locality_opt, :locality_type,
	POINT(:lon, :lat),
	:base_score,
	:poi_class,
	:keywords,
	:created,
	:refs::hstore,
	:obj::jsonb
)
ON CONFLICT (id) DO NOTHING;
