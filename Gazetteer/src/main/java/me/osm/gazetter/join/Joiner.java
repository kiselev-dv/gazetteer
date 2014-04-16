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

import me.osm.gazetter.Options;
import me.osm.gazetter.addresses.AddressesParser;
import me.osm.gazetter.striper.FeatureTypes;
import me.osm.gazetter.striper.GeoJsonWriter;
import me.osm.gazetter.striper.JSONFeature;
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
	private final AddressesParser addressesParser;
	
	public Joiner(Set<String> filter) {
		this.filter = filter;
		this.addressesParser = Options.get().getAddressesParser();
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
		
		final Map<JsonObjectWrapper, Set<JsonObjectWrapper>> index = 
				new HashMap<JsonObjectWrapper, Set<JsonObjectWrapper>>();
		
		FileUtils.handleLines(binxFile, new LineHandler() {
			
			@Override
			public void handle(String s) {
				
				JSONObject entry = new JSONObject(s);
				
				JsonObjectWrapper obj = new JsonObjectWrapper(entry.getJSONObject("obj"));
				if(index.get(obj) == null) {
					index.put(obj, new LinkedHashSet<JsonObjectWrapper>());
				}
				
				JSONArray uppers = entry.getJSONArray("boundaries");
				for(int i = 0; i < uppers.length(); i++) {
					index.get(obj).add(new JsonObjectWrapper(uppers.getJSONObject(i)));
				}
			}
			
		});

		try {
			
			boundaryIndexWriter = new PrintWriter(binxFile);
			for(Entry<JsonObjectWrapper, Set<JsonObjectWrapper>> entry : index.entrySet()) {
				
				String id = entry.getKey().getId();
				int[] levels = getLevels(entry.getValue());
				
				if(isNotDistinct(levels)) {
					log.info("{} included into more than one bigger boundaryes.", id);
					continue;
				}
				
				JSONObject result = new JSONObject();
				JSONObject object = new JSONFeature(entry.getKey().getObject());
				
				result.put("level", Integer.parseInt(object.
						getJSONObject(GeoJsonWriter.PROPERTIES).getString("admin_level")));
				
				object.put("ftype", FeatureTypes.ADMIN_BOUNDARY_FTYPE);
				result.put("id", id);
				result.put("obj", object);
				JSONObject boundaries = this.addressesParser.boundariesAsArray(object, unwrap(entry.getValue()));
				result.put("boundaries", boundaries);
				
				boundaryIndexWriter.println(result.toString());
			}
			
			boundaryIndexWriter.flush();
			boundaryIndexWriter.close();
			
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}


	private List<JSONObject> unwrap(Set<JsonObjectWrapper> set) {
		List<JSONObject> result = new ArrayList<>();
		
		for(JsonObjectWrapper s : set) {
			result.add(s.getObject());
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


	private int[] getLevels(Set<JsonObjectWrapper> set) {
		int[] result = new int[set.size()];
		int i = 0;
		for(JsonObjectWrapper obj : set) {
			result[i++] = Integer.valueOf(obj.getObject()
					.getJSONObject(GeoJsonWriter.PROPERTIES).getString("admin_level"));
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
	
	public synchronized void handleBoundaryesIndex(Map<JsonObjectWrapper, Set<JsonObjectWrapper>> hierarchy, String fileName) {
		for(Entry<JsonObjectWrapper, Set<JsonObjectWrapper>> entry : hierarchy.entrySet()) {
			
			JSONObject out = new JSONObject();
			
			out.put("file", fileName);
			JSONObject obj = new JSONObject();
			obj.put("id", entry.getKey().getId());
			obj.put(GeoJsonWriter.PROPERTIES, entry.getKey().getObject()
					.getJSONObject(GeoJsonWriter.PROPERTIES));
			out.put("obj", obj);
			
			JSONArray uppers = new JSONArray();
			
			for(JsonObjectWrapper upb : entry.getValue()) {
				JSONObject ref = new JSONObject();
				ref.put("id", upb.getObject().getString("id"));
				ref.put(GeoJsonWriter.PROPERTIES, upb.getObject().getJSONObject(GeoJsonWriter.PROPERTIES));
				
				uppers.put(ref);
			}
			out.put("boundaries", uppers);
			
			boundaryIndexWriter.println(out.toString());
		}
	}

}
