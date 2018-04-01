select 
	ts_rank(name, main, 0) as rank,
	full_text,
	osm_type || osm_id as osm_id,
	json 
from addresses, to_tsquery(:main_ts_q) main, to_tsquery(:opt_ts_q) opt
where 
   where
	name @@ main
	and
	type <> 'adrpnt'
order by rank desc
limit :limit
offset :offset;