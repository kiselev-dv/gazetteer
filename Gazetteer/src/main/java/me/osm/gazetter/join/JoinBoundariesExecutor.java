package me.osm.gazetter.join;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import me.osm.gazetter.Options;
import me.osm.gazetter.addresses.AddressesParser;
import me.osm.gazetter.join.util.BoundaryCortage;
import me.osm.gazetter.striper.GeoJsonWriter;
import me.osm.gazetter.striper.JSONFeature;
import me.osm.gazetter.utils.FileUtils;
import me.osm.gazetter.utils.FileUtils.LineHandler;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.code.externalsorting.ExternalSort;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.index.quadtree.Quadtree;

public class JoinBoundariesExecutor {
	
	private static final Logger log = LoggerFactory.getLogger(JoinBoundariesExecutor.class);
	
	private File binxFile;
	
	private Map<BoundaryCortage, BoundaryCortage> bhierarchy = 
			Collections.synchronizedMap(new HashMap<BoundaryCortage, BoundaryCortage>());
	
	private AddressesParser addressesParser;
	
	private static final AdmLvlComparator ADM_LVL_COMPARATOR = new AdmLvlComparator();
	
	private static class AdmLvlComparator implements Comparator<String> {

		private Set<Integer> lvls = new HashSet<>();
		
		@Override
		public int compare(String arg0, String arg1) {
			int i1 = Integer.parseInt(GeoJsonWriter.getAdmLevel(arg0));
			int i2 = Integer.parseInt(GeoJsonWriter.getAdmLevel(arg1));
			
			lvls.add(i1);
			lvls.add(i2);
			
			return Integer.compare(i1, i2);
		}

		public Set<Integer> getLvls() {
			return lvls;
		}
		
	}
	
	public void run(String stripesFolder, List<JSONObject> common, Set<String> filter) {

		long start = (new Date()).getTime();

		try {
			binxFile = FileUtils
					.withGz(new File(stripesFolder + "/binx.gjson"));
			
			if(binxFile.exists()) {
				List<String> bndrs = FileUtils.readLines(binxFile);
				Collections.sort(bndrs, ADM_LVL_COMPARATOR);
				FileUtils.writeLines(binxFile, bndrs);
				bndrs = null;
			}
			else {
				log.trace("Skip boundaries index sorting");
			}


			if(binxFile.exists()) {
				joinBoundaries(stripesFolder, common, filter);
			}
			else {
				log.trace("Skip boundaries index join");
			}
			
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		log.info(
				"Join boundaries done in {}",
				DurationFormatUtils.formatDurationHMS(new Date().getTime()
						- start));
	}
	
	private void joinBoundaries(String stripesFolder, List<JSONObject> common, 
			final Set<String> filter) throws Exception {
		
		addressesParser = Options.get().getAddressesParser();
		
		BufferedReader binxReader = new BufferedReader(new InputStreamReader(FileUtils.getFileIS(binxFile)));
		List<File> l = ExternalSort.sortInBatch(binxReader, binxFile.length() * 10, ADM_LVL_COMPARATOR,
                100, 100 * 1024 * 1024, Charset.forName("UTF8"), new File(stripesFolder), true, 0,
                true);
		
		binxFile = new File(stripesFolder + "/" + "binx-sorted.gjson");
		ExternalSort.mergeSortedFiles(l, binxFile, ADM_LVL_COMPARATOR, Charset.forName("UTF8"),
                true, false, true);
                
       List<Integer> lvls = new ArrayList<Integer>(ADM_LVL_COMPARATOR.getLvls());
       Collections.sort(lvls);
       
       final Iterator<Integer> lvlsi = lvls.iterator();

		if (lvls.size() >= 2) {
			
			//one of the ugliest java things.
			final int[] uppers = new int[]{lvlsi.next()};
			final int[] downs = new int[]{lvlsi.next()};
			
			final List<BoundaryCortage> ups = new ArrayList<BoundaryCortage>();
			final List<BoundaryCortage> dwns = new ArrayList<BoundaryCortage>();

			FileUtils.handleLines(binxFile, new LineHandler() {

				@Override
				public void handle(String s) {
					JSONFeature obj = new JSONFeature(s);
					Integer admLVL = Integer.valueOf(obj.getJSONObject(
							GeoJsonWriter.PROPERTIES).getString("admin_level"));
					
					if(admLVL == uppers[0]) {
						ups.add(new BoundaryCortage(obj));
					}
					else if(admLVL == downs[0]) {
						dwns.add(new BoundaryCortage(obj));
					} 
					else if(admLVL > downs[0]) {
						
						fillHierarchy(ups, dwns);

						uppers[0] = downs[0];
						downs[0] = lvlsi.next();
						
						ups.clear();
						ups.addAll(dwns);
						dwns.clear();
						dwns.add(new BoundaryCortage(obj));
					}
					else {
						throw new RuntimeException("Boundaries are not sorted properly.");
					}
					
				}

			});
		}
		
		File binxnew = new File(stripesFolder + "/binx-updated.gjson" + (Options.get().isCompress() ? ".gz" : ""));
		final PrintWriter writer = FileUtils.getPrintwriter(binxnew, false);
		
		FileUtils.handleLines(binxFile, new LineHandler() {

			@Override
			public void handle(String s) {
				JSONObject obj = new JSONObject(s);
				String id = obj.getString("id");
				
				List<JSONObject> uppers = new ArrayList<JSONObject>();
				
				for(BoundaryCortage up : getUppers(id)) {
					JSONObject o = new JSONObject();
					
					String upid = up.getId();
					o.put("id", upid);
					o.put(GeoJsonWriter.PROPERTIES, up.getProperties());
					
					
					JSONObject meta = new JSONObject();
					String osmId = StringUtils.split(upid, '-')[2];
					meta.put("id", Long.parseLong(osmId.substring(1)));
					meta.put("type", osmId.charAt(0) == 'r' ? "relation" : "way");
					
					o.put(GeoJsonWriter.META, meta);
					
					uppers.add(o);
				}
				
				if(filter == null || filter.isEmpty() || check(obj, uppers, filter)) {
					obj.put("boundaries", addressesParser.boundariesAsArray(obj, uppers));
					
					writer.println(obj.toString());
				}
				
			}

			private boolean check(JSONObject obj, List<JSONObject> uppers,
					Set<String> filter) {
				
				if(filter.contains(StringUtils.split(obj.getString("id"), '-')[2])) {
					return true;
				}
				
				for(JSONObject up : uppers) {
					if(filter.contains(StringUtils.split(up.getString("id"), '-')[2])) {
						return true;
					}
				}
				
				return false;
			}

			private List<BoundaryCortage> getUppers(String id) {
				
				List<BoundaryCortage> result = new ArrayList<BoundaryCortage>();
				
				BoundaryCortage up = bhierarchy.get(new BoundaryCortage(id));
				
				while (up != null) {
					result.add(up);
					up = bhierarchy.get(up);
				}
				
				return result;
			}
			
		});
		
		writer.flush();
		writer.close();
       
		binxFile.delete();
		binxnew.renameTo(new File(stripesFolder + "/binx.gjson" + (Options.get().isCompress() ? ".gz" : "")));
	}
	
	private void fillHierarchy(List<BoundaryCortage> ups, List<BoundaryCortage> dwns) {
		
		Quadtree qt = new Quadtree();
		for(BoundaryCortage b : dwns) {
			qt.insert(new Envelope(b.getGeometry().getCentroid().getCoordinate()), b);
		}
		
		ExecutorService es = Executors.newFixedThreadPool(Options.get().getNumberOfThreads());
		
		for(BoundaryCortage up : ups) {
			es.execute(new JoinBoundariesRunable(up, qt, bhierarchy));
		}
		
		es.shutdown();
		
		try {
			while (!es.awaitTermination(100, TimeUnit.MILLISECONDS)) {
				//still waiting
			}
		}
		catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		
	}
}
