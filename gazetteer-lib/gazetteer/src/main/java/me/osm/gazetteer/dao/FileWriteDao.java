package me.osm.gazetteer.dao;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

import me.osm.gazetteer.Options;
import me.osm.gazetteer.striper.GeoJsonWriter;
import me.osm.gazetteer.utils.FileUtils;

/**
 * Abstraction for writing in multiple "files"
 * */
public class FileWriteDao implements WriteDao {

	private static final Map<String, PrintWriter> writers = new HashMap<String, PrintWriter>();
	private File dir;


	/**
	 * @param dir directory for files
	 */
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

		boolean useGZ = Options.get().isCompress();

		PrintWriter pw = writers.get(key);
		if(pw == null) {
			synchronized(writers) {
				pw = writers.get(key);
				if(pw == null) {

					File file = new File(dir.getAbsolutePath() + "/" + key + (useGZ ? ".gz" : ""));
					pw = FileUtils.getPrintWriterWithGZAppendTrick(file, true);

					if(!file.exists()) {
						file.createNewFile();
					}

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
