package me.osm.gazetter.join;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.osm.gazetter.Options;
import me.osm.gazetter.join.out_handlers.JoinOutHandler;
import me.osm.gazetter.join.util.JoinFailuresHandler;
import me.osm.gazetter.join.util.MemorySupervizor;
import me.osm.gazetter.utils.FileUtils;

public class JoinExecutor implements JoinFailuresHandler{

	private AddrJointHandler addrPointFormatter = new AddrPointFormatter();

	private static final Logger log = LoggerFactory.getLogger(JoinExecutor.class.getName());

	private AtomicInteger stripesCounter;

	private Set<String> filter;

	private boolean dropHghNetGeometries = true;
	private boolean cleanStripes = false;
	private int throttleMemThreshold = -1;

	public JoinExecutor(Boolean skipHghNets, 
			Boolean keepHghNetGeometries, 
			Boolean cleanStripes, 
			Set<String> filter,
			Integer throttleMemThreshold) {
		this.filter = filter;

		if(skipHghNets != null) {
			this.buildStreetNetworks = !skipHghNets;
		}

		if(keepHghNetGeometries != null) {
			this.dropHghNetGeometries  = !dropHghNetGeometries;
		}

		if(cleanStripes != null) {
			this.cleanStripes  = cleanStripes;
		}
		
		if (throttleMemThreshold != null) {
			this.throttleMemThreshold = throttleMemThreshold;
		}
	}

	private JoinBoundariesExecutor jbe = new JoinBoundariesExecutor();

	public static class StripeFilenameFilter implements FilenameFilter {

		@Override
		public boolean accept(File dir, String name) {
			return name.matches("stripe[\\.\\d-_]+\\.gjson(\\.[\\d]+)?(\\.gz)?(?!.)");
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

			if (cleanStripes) {
				log.info("Clean stripes in {}", stripesFolder);
				File folder = new File(stripesFolder);
				File[] stripesFiles = folder.listFiles(STRIPE_FILE_FN_FILTER);
				for (File f : stripesFiles) {
					f.delete();
				}
				log.info("Removed {} stripes files", stripesFiles.length);
			}

			start = new Date().getTime();
			jbe.run(stripesFolder, common, filter);

		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		log.info(
				"Join boundaries done in {}",
				DurationFormatUtils.formatDurationHMS(new Date().getTime()
						- start));

		start = new Date().getTime();
		for(JoinOutHandler h : Options.get().getJoinOutHandlers()) {
			h.allDone();
		}

		log.info(
				"All handlers done in {}",
				DurationFormatUtils.formatDurationHMS(new Date().getTime()
						- start));
	}

	private final List<List<File>> fails = Collections.synchronizedList(new ArrayList<List<File>>());

	private boolean buildStreetNetworks = true;

	private void joinStripes(String stripesFolder, List<JSONObject> common) {

		int threads = Options.get().getNumberOfThreads();

		LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<Runnable>(threads);

		ThreadPoolExecutor executorService = new ThreadPoolExecutor(threads, threads, 0L,
				TimeUnit.MILLISECONDS, queue);
		
		executorService.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

		File folder = new File(stripesFolder);
		File[] stripesFiles = folder.listFiles(STRIPE_FILE_FN_FILTER);
		if (stripesFiles == null) {
			log.info("Data directory is empty, nothing to join");
			return;
		}
		
		LinkedHashMap<String, List<File>> fileByStripe = new LinkedHashMap<String, List<File>>();
		Arrays.stream(stripesFiles).forEach(f -> {
			String name = FileUtils.findStripeName(f);
			if(name != null) {
				fileByStripe.putIfAbsent(name, new ArrayList<File>(1));
				fileByStripe.get(name).add(f);
			}
		});
		
		stripesCounter = new AtomicInteger(fileByStripe.size());
		fails.clear();
		
		for(List<File> stripeF : fileByStripe.values()) {
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

		if(!fails.isEmpty()) {
			log.info("Rerun join for {} from {} files. In one thread.", fails.size(), stripesFiles.length);
		}

		List<List<File>> oneThread = new ArrayList<List<File>>(fails);
		fails.clear();
		for(List<File> stripeF : oneThread ) {
			new JoinSliceRunable(addrPointFormatter, stripeF, common,
					filter, buildStreetNetworks, dropHghNetGeometries, this, this).run();
		}

		if(!fails.isEmpty()) {
			log.error("Failed to join: {}", fails);
		}
	}

	private void tryToExecute(List<JSONObject> common, int threads,
			LinkedBlockingQueue<Runnable> queue,
			ExecutorService executorService, List<File> stripeF) {

		int ntries = 10;
		
		if (throttleMemThreshold > 0) {
			long avaibleRAMMeg = MemorySupervizor.getAvaibleRAMMeg();
			
			if (avaibleRAMMeg > throttleMemThreshold) {
				executorService.execute(new JoinSliceRunable(addrPointFormatter, stripeF,
						common, filter, buildStreetNetworks, dropHghNetGeometries, this, this));
			}
			else {
				log.info("Not enought memory to execute task {} Free mem: {}meg", stripeF, avaibleRAMMeg);
				
				while (ntries > 0 && avaibleRAMMeg < throttleMemThreshold) {
					try {
						Thread.sleep(1000);
					}
					catch (InterruptedException e) {
					}
					
					avaibleRAMMeg = MemorySupervizor.getAvaibleRAMMeg();
					ntries--;
				}
				
				if (avaibleRAMMeg > throttleMemThreshold) {
					executorService.execute(new JoinSliceRunable(addrPointFormatter, stripeF,
							common, filter, buildStreetNetworks, dropHghNetGeometries, this, this));
				}
				else {
					// Still not enough memory
					fails.add(stripeF);
					log.warn("Not enought memory to execute task {} after {} retries. Will join it later.", stripeF, 10);
				}
			}
		}
		else {
			executorService.execute(new JoinSliceRunable(addrPointFormatter, stripeF,
					common, filter, buildStreetNetworks, dropHghNetGeometries, this, this));
			
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
					throw new RuntimeException("Failed to read common part.", e);
				}
			}
		}

		return common;
	}


	public AtomicInteger getStripesCounter() {
		return stripesCounter;
	}

	@Override
	public void failed(List<File> f) {
		fails.add(f);
	}

}
