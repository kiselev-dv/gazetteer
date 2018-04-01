DROP TABLE IF EXISTS addresses;
DROP TABLE IF EXISTS pois;

-- You have to be superuser to create extension 
-- CREATE EXTENSION IF NOT EXISTS postgis;
-- CREATE EXTENSION IF NOT EXISTS hstore;

CREATE TABLE IF NOT EXISTS imports (
	import_key BIGSERIAL PRIMARY KEY,
	last_timestamp time,
	import_start time,
	import_ends time,
	region text
);

CREATE TABLE IF NOT EXISTS addresses (
	id 					text PRIMARY KEY,
	
	"type" 				text NOT NULL,
	feature_id 			text NOT NULL,
	
	osm_id 				bigint NOT NULL,
	osm_type 			char NOT NULL,
	
	full_text 			text,

	name 				tsvector,
	name_alt			tsvector,
	name_opt			text,
	
	housenumber_exact 	text,
	housenumber_array 	text[],
	
	street		 		tsvector,
	street_opt 			text,
	street_type 		text,
	
	-- Not in use for now
	postal_code 		text,
	
	-- Empty for now
	neighbourhood 		tsvector,

	-- City or town level
	locality 			tsvector,
	locality_opt	 	text,
	locality_type 		text,

	centroid 			point,
	
	-- Score base
	base_score 			real,
	
	addr_schema 		text,
	hm_match 			text,
	
	created 			timestamp,
	
	import_id 			bigint REFERENCES imports,
	
	/* References to other levels of address
	 * It's a foreign keys, but without constraints,
	 * because I don't need to have strict schema here. 
	 */
	refs 				hstore,
	
	-- Original OSM object tags
	tags 				hstore,
	
	json 				jsonb
);

CREATE INDEX idx_type ON addresses (type);
CREATE INDEX idx_full_text ON addresses USING GIN (to_tsvector('simple', full_text));

CREATE INDEX idx_name ON addresses USING GIN (name);
CREATE INDEX idx_name_alt ON addresses USING GIN (name_alt);
CREATE INDEX idx_name_opt ON addresses (name_opt);

CREATE INDEX idx_hn_exact ON addresses (housenumber_exact);
CREATE INDEX idx_hn_array ON addresses USING GIN (housenumber_array);

CREATE INDEX idx_street ON addresses USING GIN (street);
CREATE INDEX idx_street_opt ON addresses (street_opt);

CREATE INDEX idx_neighbourhood ON addresses USING GIN (neighbourhood);

CREATE INDEX idx_locality ON addresses USING GIN (locality);
CREATE INDEX idx_locality_opt ON addresses (locality_opt);

CREATE INDEX idx_centroid ON addresses USING GIST (centroid);

CREATE INDEX idx_refs ON addresses USING GIST (refs);
CREATE INDEX idx_tags ON addresses USING GIST (tags);


CREATE TABLE IF NOT EXISTS pois (
	id 					text PRIMARY KEY,
	
	"type" 				text NOT NULL,
	feature_id 			text NOT NULL,
	
	osm_id 				bigint NOT NULL,
	osm_type 			char NOT NULL,
	
	full_text 			text,

	name 				tsvector,
	name_alt			tsvector,
	name_opt			text,
	
	housenumber_exact 	text,
	housenumber_array 	text[],
	
	street		 		tsvector,
	street_opt 			text,
	street_type 		text,
	
	-- Not in use for now
	postal_code 		text,
	
	-- Empty for now
	neighbourhood 		tsvector,

	-- City or town level
	locality 			tsvector,
	locality_opt	 	text,
	locality_type 		text,

	centroid 			point,
	
	-- Score base
	base_score 			real,
	

	poi_class 			text[],
	keywords 			text[],
	
	created 			timestamp,
	
	import_id 			bigint REFERENCES imports,
	
	/* References to other levels of address
	 * It's a foreign keys, but without constraints,
	 * because I don't need to have strict schema here. 
	 */
	refs 				hstore,
	
	-- Original OSM object tags
	tags 				hstore,
	
	json 				jsonb
);


CREATE INDEX idx_poi_type ON pois (type);
CREATE INDEX idx_poi_full_text ON pois USING GIN (to_tsvector('simple', full_text));

CREATE INDEX idx_poi_name ON pois USING GIN (name);
CREATE INDEX idx_poi_name_alt ON pois USING GIN (name_alt);
CREATE INDEX idx_poi_name_opt ON pois (name_opt);

CREATE INDEX idx_poi_hn_exact ON pois (housenumber_exact);
CREATE INDEX idx_poi_hn_array ON pois USING GIN (housenumber_array);

CREATE INDEX idx_poi_street ON pois USING GIN (street);
CREATE INDEX idx_poi_street_opt ON pois (street_opt);

CREATE INDEX idx_poi_neighbourhood ON pois USING GIN (neighbourhood);

CREATE INDEX idx_poi_locality ON pois USING GIN (locality);
CREATE INDEX idx_poi_locality_opt ON pois (locality_opt);

CREATE INDEX idx_poi_centroid ON pois USING GIST (centroid);

CREATE INDEX idx_poi_refs ON pois USING GIST (refs);
CREATE INDEX idx_poi_tags ON pois USING GIST (tags);


create table if not exists ts_stat_streets (
	word text unique not null,
	ndoc integer,
	nentry integer
);

create table if not exists ts_stat_localities (
	word text unique not null,
	ndoc integer,
	nentry integer
);

