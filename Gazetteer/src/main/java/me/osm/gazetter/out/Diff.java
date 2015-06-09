package me.osm.gazetter.out;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

import me.osm.gazetter.striper.GeoJsonWriter;
import me.osm.gazetter.utils.FileUtils;
import me.osm.gazetter.utils.FileUtils.LineHandler;

import org.apache.commons.lang3.StringUtils;

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
				this.out = FileUtils.getPrintWriter(new File(out), false);
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

			final Set<String> olds = new HashSet<String>();
			
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
						out.println("+ " + s);
					}
					else {
						if (!((String)row[0]).equals(md5)) {
							if(((Date)row[1]).before(timestamp)) {
								out.println("N " + s);
							}
							else {
								olds.add(id);
							}
						}
					}
					
					map.remove(id);
					
				}
				
			});
			
			if(!map.isEmpty() || !olds.isEmpty()) {
				FileUtils.handleLines(new File(this.oldPath), new LineHandler() {

					@Override
					public void handle(String s) {
						String id = GeoJsonWriter.getId(s);
						
						if(map.containsKey(id)) {
							out.println("- " + s);
						}
						
						else if(olds.contains(id)) {
							out.println("O " + s);
						}
					}
					
				});
			}
			
			out.flush();
			out.close();
			
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
}
