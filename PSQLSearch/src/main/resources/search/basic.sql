select
	:full_match_weight * 
		ts_rank(locality || street, main, 1) *
		CASE WHEN locality_opt || street_opt LIKE :opt_like THEN 1.1 ELSE 1.0 END *
		CASE WHEN :hn_exact = ANY(housenumber_array) THEN 1.5 ELSE 1.0 END *
		CASE WHEN :hn_var && housenumber_array THEN 1.2 ELSE 1.0 END
	as rank,
	full_text as full_text, 
	osm_type || osm_id as osm_id, 
	json as json
from addresses, to_tsquery(:required_terms_or) main
where 
    locality @@ main
    and
    street @@ main
    and
    :hn_exact = ANY(housenumber_array)
union all
select rank, full_text, osm_id, json from (
	select distinct on (full_text)
		:street_match_weight * 
		ts_rank(locality || street, main, 1) *
		CASE WHEN locality_opt || street_opt LIKE :opt_like THEN 1.1 ELSE 1.0 END
		as rank,
		full_text as full_text, 
		osm_type || osm_id as osm_id, 
		json as json
	from addresses, to_tsquery(:required_terms_or) main
	where 
		(locality || street) @@ to_tsquery(:required_terms_and)
		and
    	name @@ main
	    and
	    type in ('hghnet', 'hghway')
	order by full_text
) q1
union all
select 
	:locality_match_weight * ts_rank(locality, main, 1) *
	CASE WHEN locality_opt || street_opt LIKE :opt_like THEN 1.1 ELSE 1.0 END 
	as rank,
	full_text as full_text, 
	osm_type || osm_id as osm_id, 
	json as json
from addresses, to_tsquery(:required_terms_or) main
where 
    locality @@ main
    and
    name @@ main
    and
    type in ('plcpnt', 'plcbnd', 'admbnd')	 
order by rank desc
limit :limit
offset :offset;


