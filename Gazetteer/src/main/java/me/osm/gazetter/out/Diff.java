package me.osm.gazetter.out;

import groovy.ui.SystemOutputInterceptor;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

import me.osm.gazetter.striper.GeoJsonWriter;
import me.osm.gazetter.utils.FileUtils;
import me.osm.gazetter.utils.FileUtils.LineHandler;

public class Diff {
	
	private String oldPath;
	private String newPath;
	private PrintWriter out;
	
	public Diff(String oldPath, String newPath, String out) {
		try {
			this.oldPath = oldPath;
			this.newPath = newPath;
			
			if(out.equals("-")) {
				this.out = new PrintWriter(System.out);
			}
			else {
				this.out = FileUtils.getPrintwriter(new File(out), false);
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private TreeMap<String, Object[]> map = new TreeMap<>();
	
	public void run() {
		try {
			FileUtils.handleLines(new File(this.oldPath), new LineHandler() {

				@Override
				public void handle(String s) {
					String id = GeoJsonWriter.getId(s);
					Date timestamp = GeoJsonWriter.getTimestamp(s);
					String md5 = GeoJsonWriter.getMD5(s);
					
					map.put(id, new Object[]{md5, timestamp});
				}
				
			});

			FileUtils.handleLines(new File(this.newPath), new LineHandler() {
				
				@Override
				public void handle(String s) {
					if(StringUtils.isEmpty(s)) {
						return;
					}
					
					String id = GeoJsonWriter.getId(s);
					Date timestamp = GeoJsonWriter.getTimestamp(s);
					String md5 = GeoJsonWriter.getMD5(s);
					
					Object[] row = map.get(id);
					if(row == null) {
						out.println("+" + id);
					}
					else {
						if(((String)row[0]).equals(md5)) {
							out.println("=" + id);
						}
						else {
							if(((Date)row[1]).before(timestamp)) {
								out.println("A" + id);
							}
							else {
								out.println("B" + id);
							}
						}
					}
					
					map.remove(id);
					
				}
				
			});
			
			for(Entry<String, Object[]> entry : map.entrySet()) {
				String id = entry.getKey();
				
				out.println("-" + id);
			}
			
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
}
