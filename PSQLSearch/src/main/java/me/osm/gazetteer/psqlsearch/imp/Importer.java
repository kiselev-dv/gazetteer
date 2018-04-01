package me.osm.gazetteer.psqlsearch.imp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;

import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.osm.gazetteer.psqlsearch.dao.ConnectionPool;
import me.osm.gazetteer.psqlsearch.imp.Importer.ImportException;
import me.osm.gazetteer.psqlsearch.named_jdbc_stmnt.NamedParameterPreparedStatement;

public class Importer {
	
	private static final ConnectionPool pool = ConnectionPool.getInstance();
	private static final Logger log = LoggerFactory.getLogger(Importer.class);
	
	private static final ExecutorService executor = Executors.newCachedThreadPool();
	
	private final String addrTemplate;
	private final String poiTemplate;
	
	private final NamedParameterPreparedStatement addressStmt;
	private final NamedParameterPreparedStatement poiStmt;
	
	private int batchSize = 10000;
	private int total = 0;
	private String source;

	private int addresses = 0;
	private int pois = 0;

	private ImportObjectParser parser = new ImportObjectParser();
	
	private volatile static boolean stmtInProgress = false;
	private final static Object stmtInProgressLatch = new Object();
	
	
	public static final class ImportException extends RuntimeException {
		public ImportException(Exception se) {
			super(se);
		}

		public ImportException(String msg, Exception cause) {
			super(msg, cause);
		}

		private static final long serialVersionUID = 5207702025718645246L;
	}

	private static final class SubmitStmtTask implements Runnable {
		
		private final PreparedStatement stmt;
		private final int counter;
		private final String logTemplate;

		public SubmitStmtTask(PreparedStatement stmt, String logTemplate, int counter) {
			this.stmt = stmt;
			this.counter = counter;
			this.logTemplate = logTemplate;
		}

		@Override
		public void run() {
			stmtInProgress = true;
			
			try {
				this.stmt.executeBatch();
				log.info(logTemplate, String.format(Locale.US, "%,9d", counter));
			}
			catch (SQLException e) {
				processExceptions(e);
			}
			finally {
				stmtInProgress = false;
				synchronized(stmtInProgressLatch) { 
					stmtInProgressLatch.notifyAll();
				}
			}
		}
	}
	
	public Importer(String source) {
		try {
			addrTemplate = IOUtils.toString(
					Importer.class.getResourceAsStream("/insert-address.sql"));
			
			poiTemplate = IOUtils.toString(
					Importer.class.getResourceAsStream("/insert-poi.sql"));
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		
		try {
			Connection connection = pool.getConnection();
			
			addressStmt = NamedParameterPreparedStatement
					.createNamedParameterPreparedStatement(connection, addrTemplate);
			
			poiStmt = NamedParameterPreparedStatement
					.createNamedParameterPreparedStatement(connection, poiTemplate);
			
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
		
		this.source = source;
	}
	
	public void run() throws ImportException {
		
		log.info("Read from {}", source);
		
		try {
			GZIPInputStream is = new GZIPInputStream(new FileInputStream(new File(source)));
			BufferedReader reader = new BufferedReader(new InputStreamReader(is, "utf8"));

			try {
				String line = reader.readLine();
				while (line != null) {
					
					total ++;
					JSONObject obj = new JSONObject(line);
					
					boolean isPoi = "poipnt".equals(obj.getString("type"));
					NamedParameterPreparedStatement stm = isPoi ? poiStmt : addressStmt;
					if(parser.addFeature(stm, obj)) {
						if (isPoi) {
							pois ++;
						}
						else {
							addresses ++;
						}
					}
					
					submitBatch(addressStmt, "{} addresses imported", addresses);
					submitBatch(poiStmt, "{} pois imported", pois);
					
					line = reader.readLine();
				}
				this.done();
			}
			catch (IOException e) {
				throw new ImportException(e);
			}
			finally {
				IOUtils.closeQuietly(reader);
			}
		}
		catch (Exception e) {
			throw new ImportException(e);
		}
	}
	
	private void submitBatch(NamedParameterPreparedStatement stmt, 
			String logTemplate, int counter) throws ImportException {
		
		if (counter > 0 && counter % batchSize == 0) {
			// Don't overflow statements submit queue
			while (stmtInProgress) {
				synchronized(stmtInProgressLatch) {
					try {
						stmtInProgressLatch.wait();
					}
					catch (InterruptedException e) {
						
					}
				}
			}
			
			executor.submit(new SubmitStmtTask(stmt, logTemplate, counter));
			
//			try {
//				stmt.executeBatch();
//				log.info(logTemplate, counter);
//			}
//			catch (SQLException se) {
//				processExceptions(se);
//			}
		}
		
	}

	private static void processExceptions(SQLException se) throws ImportException {
		SQLException firstException = se.getNextException();
		if (firstException != null) {
			throw new ImportException(firstException);
		}
		throw new ImportException(se);
	}
	
	public int rowsImported() {
		return addresses;
	}
	
	public int rowsTotal() {
		return total;
	}
	
	protected void done() throws ImportException {
		try {
			addressStmt.executeBatch();
			poiStmt.executeBatch();
		} catch (SQLException se) {
			processExceptions(se);
		}
		log.info("{} rows imported", total);
	}

}
