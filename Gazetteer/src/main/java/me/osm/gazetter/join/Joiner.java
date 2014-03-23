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
	
	private static final ExecutorService executorService = Executors.newFixedThreadPool(4);
	
	private static final AddrJointHandler addrPointFormatter = (AddrJointHandler) new AddrPointFormatter();
	
	private static final Logger log = LoggerFactory.getLogger(Joiner.class.getName());
	
	public static AtomicInteger counter = new AtomicInteger(); 
	
	public static class StripeFilenameFilter implements FilenameFilter {
		
		@Override
		public boolean accept(File dir, String name) {
			return name.startsWith("stripe");
		}
	
	}
	
	public static final StripeFilenameFilter STRIPE_FILE_FN_FILTER = new StripeFilenameFilter();
	

	public static void run(String stripesFolder, String coomonPartFile) {

		long start = (new Date()).getTime();
		
		List<JSONObject> common = getCommonPart(coomonPartFile);
		
		File folder = new File(stripesFolder);
		for(File stripeF : folder.listFiles(STRIPE_FILE_FN_FILTER)) {
			executorService.execute(new JoinSliceTask(addrPointFormatter, stripeF, common));
		}
		
		for(File stripeF : folder.listFiles(STRIPE_FILE_FN_FILTER)) {
			executorService.execute( new SortAndUpdateTask(stripeF));
		}
		
		executorService.shutdown();
		
		try {
			executorService.awaitTermination(1, TimeUnit.HOURS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		log.info("{} lines was updated.", SortAndUpdateTask.countUpdatedLines());
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
					e.printStackTrace();
				}
			}
		}
		
		return common;
	}

}
