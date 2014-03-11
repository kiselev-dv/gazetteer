package me.osm.gazetter.pointlocation;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import me.osm.gazetter.striper.GeoJsonWriter;
import me.osm.gazetter.utils.FileUtils;

public class SortAndUpdateTask implements Runnable {

	private static final String TIMESTAMP_PATTERN = "\"" + GeoJsonWriter.TIMESTAMP +  "\":\"";
	private static final String ID_PATTERN = "\"id\":\"";
	private File stripeF;
	private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.S");

	public SortAndUpdateTask(File stripeF) {
		this.stripeF = stripeF;
	}

	@Override
	public void run() {
		List<String> lines = FileUtils.readLines(stripeF);
		Collections.sort(lines, new Comparator<String>() {

			@Override
			public int compare(String paramT1, String paramT2) {
				String id1 = getId(paramT1); 
				String id2 = getId(paramT2);
				
				if(id1.equals(id2)) {
					Date d1 = getTimestamp(paramT1);
					Date d2 = getTimestamp(paramT2);
					
					return d2.compareTo(d1);
				}
				
				return id1.compareTo(id2);
			}
			
		});
		
		Iterator<String> iterator = lines.iterator();
		
		String prevId = null;
		Date prevTimestamp = null;
		
		while(iterator.hasNext()) {
			String line = iterator.next();
			
			String id = getId(line); 
			Date timestamp = getTimestamp(line);
			
			if(prevId!= null && prevTimestamp != null && id.equals(prevId) && timestamp.before(prevTimestamp)) {
				iterator.remove();
			}
			
			prevId = id;
			prevTimestamp = timestamp;
		}
		
		try {
			org.apache.commons.io.FileUtils.writeLines(stripeF, lines);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
		
	}

	public static Date getTimestamp(String line) {
		int begin = line.indexOf(TIMESTAMP_PATTERN) + TIMESTAMP_PATTERN.length();
		int end = line.indexOf("\"", begin + TIMESTAMP_PATTERN.length());
		try {
			return sdf.parse(line.substring(begin, end - 1));
		} catch (ParseException e) {
			e.printStackTrace();
			System.exit(1);
		}
		return null;
	}

	public static String getId(String line) {
		int begin = line.indexOf(ID_PATTERN) + ID_PATTERN.length();
		int end = line.indexOf("\"", begin + ID_PATTERN.length());
		return line.substring(begin, end);
	}

}
