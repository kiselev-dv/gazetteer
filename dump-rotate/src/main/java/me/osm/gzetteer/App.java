package me.osm.gzetteer;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.Days;
import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App 
{
	private static File base;
	
	private static final Logger log = LoggerFactory.getLogger(App.class);
	
    public static void main( String[] args )
    {
    	try {
    		
    		base = new File(System.getProperty("user.dir"));
    		if (args.length > 0) {
    			base = new File(args[0]);
    		}
    		
    		File dumps = new File(base, "dumps");
    		log.info("Looking for dumps in {}", dumps.getAbsolutePath());
    		
    		LocalDate today = new LocalDate();
    		
    		Map<String, List<File>> dumpsByRegion = new HashMap<String, List<File>>(); 
    		
    		Iterator<File> it = FileUtils.iterateFiles(dumps, new String[]{"json.gz"}, true);
    		while (it.hasNext()) {
    			File f = it.next();
    			LocalDate dumpDate = dateFomName(f);
    			
    			if (dumpDate != null) {
    				
    				int days = Days.daysBetween(dumpDate, today).getDays();
    				int dayOfWeek = dumpDate.dayOfWeek().get();
    				
    				if (dayOfWeek != 1 && days > 3) {
    					log.info("Delete {}", f.toString());
    					f.delete();
    					continue;
    				}
    				
    				if (days > 30) {
    					log.info("Delete {}", f.toString());
    					f.delete();
    					continue;
    				}
    				
    				String region = f.getParentFile().getName();
    				if (dumpsByRegion.get(region) == null) {
    					dumpsByRegion.put(region, new ArrayList<File>());
    				}

    				dumpsByRegion.get(region).add(f);
    			}
    			else {
    				log.info("Skip {}", f.toString());
    			}
    			
    		}
    		
    		for (Map.Entry<String, List<File>> entry : dumpsByRegion.entrySet()) {
    			generateDiffs(entry.getKey(), entry.getValue());
    		}
    	}
    	catch (Throwable t) {
    		t.printStackTrace();
    	}
    }

	private static void generateDiffs(String region, List<File> dumps) {
		
		
		
		Collections.sort(dumps, new Comparator<File>() {

			public int compare(File f1, File f2) {
				LocalDate d1 = dateFomName(f1);
				LocalDate d2 = dateFomName(f2);
				
				return d1.compareTo(d2);
			}
			
		});
		
		List<String> dumpNames = dumps.stream().map(f -> f.getName())
			.collect(Collectors.toList());
		log.info("Files in {} region: [{}]", 
				region, 
				StringUtils.join(dumpNames,	", "));
		
		File fl = null;
		for (File f : dumps) {
			
			if (fl != null && daysBetween(fl, f) == 1) {
				File outFolder = new File(new File(base, "diffs"), region);
				outFolder.mkdirs();
				generateDiff(fl, f, outFolder, region);
			}
			
			fl = f;
		}
		
	}

	private static void generateDiff(File fnew, File fold, File outFolder, String logPrefix) {
		try {
			LocalDate dateNew = dateFomName(fnew);
			LocalDate dateOld = dateFomName(fold);
			
			if (dateNew.isBefore(dateOld)) {
				generateDiff(fold, fnew, outFolder, logPrefix);
				return;
			}
			
			log.info("Generate diff between {} and {}", fold.getName(), fnew.getName());
			
			String oldDateString = StringUtils.remove(fold.getName(), ".json.gz");
			String newDateString = StringUtils.remove(fnew.getName(), ".json.gz");
			
			String diffName = oldDateString + "_" + newDateString + ".diff.gz";
			
			File diffFile = new File(outFolder, diffName);
			File binFile = new File(new File(base, "bin"), "gazetteer.jar");

			if(!diffFile.exists()) {
				String outFilePath = diffFile.getAbsolutePath();
				String newPath = fnew.getAbsolutePath();
				String oldPath = fold.getAbsolutePath();
				String binPath = binFile.getAbsolutePath();
				
				String cmd = String.format(
							"java -jar %s --log-prefix %s diff --old %s --new %s --out-file %s", 
							binPath, logPrefix, oldPath, newPath, outFilePath);
				
				log.info("Call {}", cmd);
				
				callCmd(cmd);
				
			}
			else {
				log.info("Diff {} already exists", diffName);
			}
			
			postProcessDiff(diffFile);
			
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static void callCmd(String cmd) throws IOException {
		Process process = Runtime.getRuntime().exec(cmd);
		
		BufferedReader stdInput = new BufferedReader(new 
				InputStreamReader(process.getInputStream()));

		BufferedReader stdError = new BufferedReader(new 
		     InputStreamReader(process.getErrorStream()));
		
		String s = null;
		
		// read the output from the command
		while ((s = stdInput.readLine()) != null) {
		    System.out.println(s);
		}
		
		// read any errors from the attempted command
		while ((s = stdError.readLine()) != null) {
		    System.out.println(s);
		}
	}

	private static void postProcessDiff(File diffFile) {
		
	}

	private static int daysBetween(File fl, File f) {
		LocalDate d1 = dateFomName(fl);
		LocalDate d2 = dateFomName(f);
		
		return Days.daysBetween(d1, d2).getDays();
	}

	private static LocalDate dateFomName(File f) {
		try {
			return new LocalDate(StringUtils.remove(f.getName(), ".json.gz"));
		}
		catch (IllegalArgumentException e) {
			return null;
		}
	}
    
    
}
