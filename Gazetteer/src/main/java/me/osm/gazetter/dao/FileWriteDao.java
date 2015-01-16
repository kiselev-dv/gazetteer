package me.osm.gazetter.dao;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

import me.osm.gazetter.Options;
import me.osm.gazetter.striper.GeoJsonWriter;
import me.osm.gazetter.utils.FileUtils;

public class FileWriteDao implements WriteDao {

	private static final Map<String, PrintWriter> writers = new HashMap<String, PrintWriter>();
	private File dir;
	
	public FileWriteDao(File dir) {
		this.dir = dir;
		dir.mkdirs();
	}
	
	@Override
	public void write(String line, String key) throws IOException {
		PrintWriter w = getWriter(key);
		synchronized (w) {
			assert GeoJsonWriter.getTimestamp(line) != null;
			w.println(line);
		}
	}

	private PrintWriter getWriter(String key) throws IOException {

		PrintWriter pw = writers.get(key);
		if(pw == null) {
			synchronized(writers) {
				pw = writers.get(key);
				if(pw == null) {
					boolean useGZ = Options.get().isCompress();

					File file = new File(dir.getAbsolutePath() + "/" + key + (useGZ ? ".gz" : ""));
					if(!file.exists()) {
						file.createNewFile();
					}
					
					pw = FileUtils.getPrintwriter(file, !useGZ);
					writers.put(key, pw);
				}
			}
		}
		return pw;
	}

	@Override
	public void close() {
		for(PrintWriter writer : writers.values()) {
			writer.flush();
			writer.close();
		}
	}

}
