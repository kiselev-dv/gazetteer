package me.osm.gazetter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import me.osm.gazetter.striper.GeoJsonWriter;
import me.osm.gazetter.utils.FileUtils;
import me.osm.gazetter.utils.FileUtils.LineHandler;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.LocalDateTime;
import org.json.JSONObject;

import com.google.code.externalsorting.ExternalSort;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.io.WKTReader;

public class BoundariesFallbacker {
	
	private String fallbackPath;
	private HashSet<String> fallbackTypes;

	private PrintWriter writer;
	
	//It shouldn't be very big, I think
	private Map<String, String> cache = null;
	private File file;
	
	public BoundariesFallbacker(String fallbackPath, List<String> storeTypes) {
		
		this.fallbackPath = StringUtils.stripToNull(fallbackPath);
		
		if(this.fallbackPath != null) {
			
			this.fallbackTypes = new HashSet<String>(storeTypes);
			
			if(this.fallbackTypes == null || this.fallbackTypes.isEmpty()) {
				this.fallbackTypes.addAll(Arrays.asList(
						"boundary:2", 
						"boundary:3", 
						"boundary:4", 
						"boundary:5", 
						"boundary:6"));
			}

			try {
				file = new File(this.fallbackPath);
				if(file.exists()) {
					buildCache();
				}
				
				if(!file.exists()) {
					file.createNewFile();
				}
				
				OutputStream os = new FileOutputStream(file, true);
				writer = new PrintWriter(new OutputStreamWriter(os, "UTF8"));
				
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}
	
	public void saveBoundary(JSONObject feature, MultiPolygon geometry) {
		if(writer != null) {
			JSONObject properties = feature.getJSONObject(GeoJsonWriter.PROPERTIES);
			
			String btype = "boundary:" + StringUtils.stripToEmpty(properties.optString("admin_level"));
			if(fallbackTypes.contains(btype)) {
				
				String id = StringUtils.split(feature.getString("id"), '-')[2];
				String wkt = geometry.toString();
				writer.println(id + "\t" + StringUtils.removeEnd(GeoJsonWriter.getNowTimestampString(), "Z")  + "\t" + wkt);
			}
		}
		
	}

	public MultiPolygon getGeometry(String id) {
		if(file == null) {
			return null;
		}
		
		try {
			
			if(cache != null) {
				String wkt = cache.get(id);
				if(wkt != null) {
					return (MultiPolygon)new WKTReader().read(wkt);
				}
			}
			
			
		}
		catch (Exception e) {
			return null;
		}
		
		return null;
	}

	private void buildCache() throws IOException {
		if(file != null) {
			cache = new HashMap<String, String>();
			for(String s : FileUtils.readLines(file)) {
				String[] split = StringUtils.split(s, '\t');
				if(split != null && split.length == 3) {
					String id = split[0]; 
					String geom = split[1];
					
					cache.put(id, geom);
				}
			}
		}
	}

	public void close() {
		if(writer != null) {
			writer.flush();
			writer.close();
		}
		
		sortAndMerge();
	}
	
	private class IdTimestampGeometry{
		public String id = null;
		public LocalDateTime timestamp = null;
		public String geometry = null;
	}

	private void sortAndMerge() {
		try {
			ExternalSort.sort(file, file);
			
			File tmp = new File(this.fallbackPath + ".tmp");
			OutputStream os = new FileOutputStream(tmp, true);
			final PrintWriter tmpW = new PrintWriter(new OutputStreamWriter(os, "UTF8"));
			final IdTimestampGeometry itg = new IdTimestampGeometry();
			
			LineHandler handler = new LineHandler() {
				
				@Override
				public void handle(String s) {
					String[] split = StringUtils.split(s, '\t');
				
					if(split[0].equals(itg.id)) {
						LocalDateTime t = new LocalDateTime(StringUtils.removeEnd(split[1], "Z"));
						if(itg.timestamp == null || t.isAfter(itg.timestamp)) {
							itg.geometry = split[2];
							itg.timestamp = t;
						}
					}
					else {
						
						if(itg.id != null) {
							tmpW.println(itg.id + '\t' + itg.timestamp.toString() + '\t' + itg.geometry);
						}
						
						itg.id = split[0];
						itg.geometry = split[2];
						itg.timestamp = LocalDateTime.parse(split[1]); 
					}
				}
				
			};
			
			FileUtils.handleLines(file, handler);
			if(itg.id != null) {
				tmpW.println(itg.id + '\t' + itg.timestamp.toString() + '\t' + itg.geometry);
			}
			
			tmpW.flush();
			tmpW.close();
			
			tmp.renameTo(file);
			
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
