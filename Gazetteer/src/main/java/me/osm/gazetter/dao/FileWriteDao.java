package me.osm.gazetter.dao;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import me.osm.gazetter.Options;
import me.osm.gazetter.striper.GeoJsonWriter;
import me.osm.gazetter.utils.FileUtils;

/**
 * Abstraction for writing in multiple "files"
 * */
public class FileWriteDao implements WriteDao {

	private final boolean PARTITION_STRIPE_FILES;

	private static final Map<String, PrintWriter> writers = new HashMap<String, PrintWriter>();
	private File dir;
	
	
	/**
	 * @param dir directory for files
	 */
	public FileWriteDao(File dir, boolean partition) {
		this.PARTITION_STRIPE_FILES = partition;
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
					
					if(!file.exists()) {
						file.createNewFile();
					}
					else if (PARTITION_STRIPE_FILES) {
						List<Integer> existing = Files.walk(dir.toPath())
							.filter(Files::isRegularFile)
							.map(p -> p.getFileName().toString())
							.filter(name -> name.startsWith(key))
							.map(name -> name.replace(key, "").replace(".gz", "").replace(".", ""))
							.map(name -> {
								try {
									return Integer.parseInt(name);
								}
								catch (Exception e) {
									return 0;
								}
							})
							.collect(Collectors.toList());
						
						int partition = 0;
						if (!existing.isEmpty()) {
							partition = existing.stream().reduce(Math::max).get() + 1;
						}
						
						file = new File(dir.getAbsolutePath() + "/" + key + "." + partition + (useGZ ? ".gz" : ""));
					}
					pw = FileUtils.getPrintWriterWithGZAppendTrick(file, true);

					
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
