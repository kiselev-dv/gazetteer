package me.osm.gazetter.out;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import me.osm.gazetter.striper.GeoJsonWriter;
import me.osm.gazetter.utils.FileUtils;
import me.osm.gazetter.utils.FileUtils.LineHandler;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Diff {
	
	private static final Logger log = LoggerFactory.getLogger(Diff.class); 
	
	private String oldPath;
	private String newPath;
	private PrintWriter out;
	
	private boolean fillOld = false;
	
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
			log.info("Read {}", oldPath);
			
			FileUtils.handleLines(new File(this.oldPath), new LineHandler() {

				@Override
				public void handle(String s) {
					String id = GeoJsonWriter.getId(s);
					Date timestamp = GeoJsonWriter.getTimestamp(s);
					String md5 = GeoJsonWriter.getMD5(s);
					
					map.put(id, new Object[]{md5, timestamp});
				}
				
			});
			
			log.info("{} lines were readed", map.size());

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
			
			// В мапе сейчас содержатся только те объекты 
			// которых нет в --new
			
			// В olds сотержаться айдишки тех, кто есть в --old и они новее
			// по идее таких быть не должно, кроме случая если перепутали
			// --new и --old местами
			
			boolean fillDataForRemoved = !map.isEmpty() && fillOld;
			boolean newer = !olds.isEmpty();
			
			if(newer) {
				log.warn("There are objects in --old with newer timestamps");
			}
			
			if(fillDataForRemoved || newer) {
				FileUtils.handleLines(new File(this.oldPath), new LineHandler() {
					
					@Override
					public void handle(String s) {
						String id = GeoJsonWriter.getId(s);
						
						if(map.containsKey(id)) {
							if(fillOld) {
								out.println("- " + s);
							}
							else {
								out.println("- " + "{\"id\":\"" + id + "\"}");
							}
						}
						
						else if(olds.contains(id)) {
							out.println("O " + s);
						}
					}
					
				});
			}
			else if (!fillOld) {
				for(Entry<String, Object[]> entry : map.entrySet()) {
					out.println("- " + "{\"id\":\"" + entry.getKey() + "\"}");
				}
			}
			
			out.flush();
			out.close();
			
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
}
