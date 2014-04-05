package me.osm.gazetter.join;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Joiner {
	
	private AddrJointHandler addrPointFormatter = (AddrJointHandler) new AddrPointFormatter();
	
	private static final Logger log = LoggerFactory.getLogger(Joiner.class.getName());
	
	private AtomicInteger stripesCounter;
	
	public static class StripeFilenameFilter implements FilenameFilter {
		
		@Override
		public boolean accept(File dir, String name) {
			return name.startsWith("stripe");
		}
	
	}
	
	public static final StripeFilenameFilter STRIPE_FILE_FN_FILTER = new StripeFilenameFilter();
	

	public void run(String stripesFolder, String coomonPartFile) {

		long start = (new Date()).getTime();
		
		List<JSONObject> common = getCommonPart(coomonPartFile);
		
		ExecutorService executorService = Executors.newFixedThreadPool(4);
		
		File folder = new File(stripesFolder);
		File[] stripesFiles = folder.listFiles(STRIPE_FILE_FN_FILTER);
		stripesCounter = new AtomicInteger(stripesFiles.length); 
		for(File stripeF : stripesFiles) {
			executorService.execute(new JoinSliceTask(addrPointFormatter, stripeF, common, this));
		}
		
		executorService.shutdown();
		try {
			executorService.awaitTermination(1, TimeUnit.HOURS);
		} catch (InterruptedException e) {
			throw new RuntimeException("Executor service shutdown failed.", e);
		}
		
		log.info("Join done in {}", DurationFormatUtils.formatDurationHMS(new Date().getTime() - start));
	}


	private static List<JSONObject> getCommonPart(String coomonPartFile) {
		List<JSONObject> common = new ArrayList<>();
		
		if(coomonPartFile != null) {
			
			File cpf = new File(coomonPartFile);
			
			if(cpf.exists()) {
				try {
					
					JSONArray commonArray = new JSONArray(IOUtils.toString(new FileInputStream(cpf)));
					for(int i = 0; i < commonArray.length(); i++) {
						common.add(commonArray.getJSONObject(i));
					}
					
				} catch (Exception e) {
					throw new RuntimeException("Failed to read coomon part.", e);
				}
			}
		}
		
		return common;
	}


	public AtomicInteger getStripesCounter() {
		return stripesCounter;
	}

}
