package me.osm.gazetteer.psqlsearch.stats;

import java.sql.SQLException;

import me.osm.gazetteer.psqlsearch.dao.ConnectionPool;

public class UpdateStats {
	
	private static final String INSERT_STREETS_STATS = 
			"insert into ts_stat_streets "
		  + "SELECT * FROM ts_stat('SELECT matched_street from addresses')";
	private static final String TRUNCATE_STREETS_STATS = "truncate table ts_stat_streets";
	
	private static final ConnectionPool pool = ConnectionPool.getInstance();
	
	public void update() {
		try {
			pool.getConnection().prepareStatement(TRUNCATE_STREETS_STATS).executeQuery();
			pool.getConnection().prepareStatement(INSERT_STREETS_STATS).executeQuery();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

}
