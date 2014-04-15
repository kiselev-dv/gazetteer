package me.osm.gazetter.join;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import me.osm.gazetter.utils.FileUtils;
import me.osm.gazetter.utils.FileUtils.LineHandler;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.codehaus.groovy.runtime.ArrayUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Joiner {
	
	private AddrJointHandler addrPointFormatter = (AddrJointHandler) new AddrPointFormatter();
	
	private static final Logger log = LoggerFactory.getLogger(Joiner.class.getName());
	
	private AtomicInteger stripesCounter;
	
	private Set<String> filter;
	
	private PrintWriter boundaryIndexWriter;
	
	public Joiner(Set<String> filter) {
		this.filter = filter;
	}
	
	public static class StripeFilenameFilter implements FilenameFilter {
		
		@Override
		public boolean accept(File dir, String name) {
			return name.startsWith("stripe");
		}
	
	}
	
	public static final StripeFilenameFilter STRIPE_FILE_FN_FILTER = new StripeFilenameFilter();

	private File binxFile;
	

	public void run(String stripesFolder, String coomonPartFile) {

		long start = (new Date()).getTime();
		
		try {
			binxFile = new File(stripesFolder + "/binx.gjson");
			boundaryIndexWriter = new PrintWriter(binxFile);
		} catch (FileNotFoundException e1) {
			throw new RuntimeException(e1);
		}
		
		List<JSONObject> common = getCommonPart(coomonPartFile);
		
		ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		
		File folder = new File(stripesFolder);
		File[] stripesFiles = folder.listFiles(STRIPE_FILE_FN_FILTER);
		stripesCounter = new AtomicInteger(stripesFiles.length); 
		for(File stripeF : stripesFiles) {
			executorService.execute(new JoinSliceTask(addrPointFormatter, stripeF, common, filter, this));
		}
		
		executorService.shutdown();
		try {
			while(!executorService.awaitTermination(1, TimeUnit.MINUTES)) {
				//still waiting
			}
			
		} catch (InterruptedException e) {
			throw new RuntimeException("Executor service shutdown failed.", e);
		}
		
		boundaryIndexWriter.flush();
		boundaryIndexWriter.close();
		
		reduceBoundariesIndex();
		
		log.info("Join done in {}", DurationFormatUtils.formatDurationHMS(new Date().getTime() - start));
	}


	private void reduceBoundariesIndex() {
		final Map<String, Set<String>> index = new HashMap<String, Set<String>>();
		
		FileUtils.handleLines(binxFile, new LineHandler() {
			
			@Override
			public void handle(String s) {
				
				JSONObject obj = new JSONObject(s);
				
				String id = obj.getString("id");
				if(index.get(id) == null) {
					index.put(id, new LinkedHashSet<String>());
				}
				
				JSONArray uppers = obj.getJSONArray("uppers");
				for(int i = 0; i < uppers.length(); i++) {
					String upb = uppers.getString(i);
					index.get(id).add(upb);
				}
			}
			
		});

		try {
			
			boundaryIndexWriter = new PrintWriter(binxFile);
			for(Entry<String, Set<String>> entry : index.entrySet()) {
				
				String[] split = StringUtils.split(entry.getKey());
				String id = split[0];
				int[] levels = getLevels(entry.getValue());
				
				if(isNotDistinct(levels)) {
					log.info("{} included into more than one bigger boundaryes.", id);
					continue;
				}
				
				JSONObject result = new JSONObject();
				result.put("level", Integer.parseInt(split[1]));
				result.put("id", id);
				result.put("uppers", new JSONArray(getIds(entry.getValue())));
				
				boundaryIndexWriter.println(result.toString());
			}
			
			boundaryIndexWriter.flush();
			boundaryIndexWriter.close();
			
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}


	private List<String> getIds(Set<String> value) {
		List<String> result = new ArrayList<>();
		
		for(String s : value) {
			result.add(StringUtils.split(s)[0]);
		}
		
		return result;
	}


	private boolean isNotDistinct(int[] levels) {
		int last = -1;
		
		for(int curent : levels) {
			if(curent == last) {
				return true;
			}
			last = curent;
		}
		
		return false;
	}


	private int[] getLevels(Set<String> ids) {
		int[] result = new int[ids.size()];
		int i = 0;
		for(String id : ids) {
			result[i++] = Integer.valueOf(StringUtils.split(id)[1]);
		} 
		return result;
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
	
	public synchronized void handleBoundaryesIndex(Map<String, Set<String>> hierarchy, String fileName) {
		for(Entry<String, Set<String>> entry : hierarchy.entrySet()) {
			JSONObject out = new JSONObject();
			out.put("file", fileName);
			out.put("id", entry.getKey());
			JSONArray uppers = new JSONArray(entry.getValue());
			out.put("uppers", uppers);
			
			boundaryIndexWriter.println(out.toString());
		}
	}

}
