package me.osm.gazetteer.psqlsearch.dao;

import java.sql.Connection;
import java.sql.SQLException;

import com.jolbox.bonecp.BoneCP;
import com.jolbox.bonecp.BoneCPConfig;

public class ConnectionPool {
	
	private static final ConnectionPool INSTANCE = new ConnectionPool();
	
	public static final ConnectionPool getInstance() {
		return INSTANCE;
	}
	
	private BoneCP connectionPool = null;

	private ConnectionPool() {
		
		try {
			Class.forName("org.postgresql.Driver");
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
		
		BoneCPConfig config = new BoneCPConfig();
		config.setJdbcUrl("jdbc:postgresql://localhost/gazetteer"); // jdbc url specific to your database, eg jdbc:mysql://127.0.0.1/yourdb
		config.setUsername("gazetteer"); 
		config.setPassword("gazetteer");
		config.setMinConnectionsPerPartition(5);
		config.setMaxConnectionsPerPartition(10);
		config.setPartitionCount(1);
		try {
			connectionPool = new BoneCP(config);
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}

	}
	
	public Connection getConnection() throws SQLException {
		return connectionPool.getConnection();
	}

}
