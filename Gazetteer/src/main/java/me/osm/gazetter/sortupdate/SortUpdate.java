package me.osm.gazetter.sortupdate;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import me.osm.gazetter.join.Joiner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SortUpdate {
	
	private static final Logger log = LoggerFactory.getLogger(SortUpdate.class);
	private String dataDir;
	
	public SortUpdate(String dataDir) {
		this.dataDir = dataDir;
	}
	
	public void run() {
		ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		
		for(File stripeF : new File(dataDir).listFiles(Joiner.STRIPE_FILE_FN_FILTER)) {
			executorService.execute( new SortAndUpdateTask(stripeF));
		}
		
		executorService.shutdown();
		
		try {
			while(!executorService.awaitTermination(1, TimeUnit.MINUTES)) {
				//wait for end
			}
		} catch (InterruptedException e) {
			throw new RuntimeException("Execution service shutdown awaiting interrupted.", e);
		}
		
		log.info("Update slices done. {} lines was updated.", SortAndUpdateTask.countUpdatedLines());
	}
}
