package me.osm.gazetter.join;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import me.osm.gazetter.Options;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JoinExecutor implements JoinFailuresHandler{
	
	private AddrJointHandler addrPointFormatter = new AddrPointFormatter();
	
	private static final Logger log = LoggerFactory.getLogger(JoinExecutor.class.getName());
	
	private AtomicInteger stripesCounter;
	
	private Set<String> filter;
	
	public JoinExecutor(Set<String> filter) {
		this.filter = filter;
	}

	private JoinBoundariesExecutor jbe = new JoinBoundariesExecutor();
	
	
	public static class StripeFilenameFilter implements FilenameFilter {
		
		@Override
		public boolean accept(File dir, String name) {
			return name.matches("stripe\\d+\\.gjson(\\.gz)?");
		}
	
	}
	
	public static final StripeFilenameFilter STRIPE_FILE_FN_FILTER = new StripeFilenameFilter();

	public void run(String stripesFolder, String coomonPartFile) {

		long start = (new Date()).getTime();

		try {
			List<JSONObject> common = getCommonPart(coomonPartFile);

			joinStripes(stripesFolder, common);

			log.info(
					"Join stripes done in {}",
					DurationFormatUtils.formatDurationHMS(new Date().getTime()
							- start));
			
			start = new Date().getTime();
			jbe.run(stripesFolder, common, filter);
			
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		log.info(
				"Join boundaries done in {}",
				DurationFormatUtils.formatDurationHMS(new Date().getTime()
						- start));
	}

	private final List<File> fails = Collections.synchronizedList(new ArrayList<File>());;
	
	private void joinStripes(String stripesFolder, List<JSONObject> common) {
		
		//ExecutorService executorService = Executors.newFixedThreadPool(Options.get().getNumberOfThreads());
		int threads = Options.get().getNumberOfThreads();
		
		LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<Runnable>(threads);
		ExecutorService executorService = new ThreadPoolExecutor(threads, threads, 0L, 
				TimeUnit.MILLISECONDS, queue);
		
		File folder = new File(stripesFolder);
		File[] stripesFiles = folder.listFiles(STRIPE_FILE_FN_FILTER);
		stripesCounter = new AtomicInteger(stripesFiles.length); 
		fails.clear();
		for(File stripeF : stripesFiles) {
			tryToExecute(common, threads, queue, executorService, stripeF);
		}
		
		executorService.shutdown();
		try {
			while(!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
				//still waiting
			}
			
		} catch (InterruptedException e) {
			throw new RuntimeException("Executor service shutdown failed.", e);
		}
		
		log.info("Rerun join for {} from {} files. In one thread.", fails.size(), stripesFiles.length);
		
		ArrayList<File> oneThread = new ArrayList<File>(fails);
		fails.clear();
		for(File stripeF : oneThread ) {
			new JoinSliceRunable(addrPointFormatter, stripeF, common, filter, this, this).run();
		}
		
		if(!fails.isEmpty()) {
			log.error("Failed to join: {}", fails);
		}
	}

	private void tryToExecute(List<JSONObject> common, int threads,
			LinkedBlockingQueue<Runnable> queue,
			ExecutorService executorService, File stripeF) {
		
		long avaibleRAMMeg = MemorySupervizor.getAvaibleRAMMeg();
		if(queue.size() < threads && avaibleRAMMeg > 500) {
			log.trace("Send {} to execution queue. Free mem: {}meg", stripeF, avaibleRAMMeg);
			executorService.execute(new JoinSliceRunable(addrPointFormatter, stripeF, common, filter, this, this));
		}
		else {
			try {
				Thread.sleep(5000);
			}
			catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
			tryToExecute(common, threads, queue, executorService, stripeF);
		}
	}

	public static List<JSONObject> getCommonPart(String coomonPartFile) {
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
	
	@Override
	public void failed(File f) {
		fails.add(f);
	}
	
}
