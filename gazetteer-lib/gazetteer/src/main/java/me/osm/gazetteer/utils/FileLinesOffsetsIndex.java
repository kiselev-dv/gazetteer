package me.osm.gazetteer.utils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.collections4.map.LRUMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileLinesOffsetsIndex {

	private static final Logger log = LoggerFactory.getLogger(FileLinesOffsetsIndex.class);

	private final Map<String, String> cachedLines;
	private final Map<String, Long> offsets;

	private Accessor accessor;
	private boolean built = false;
	private RandomAccessFile randomAccessFile;

	private int hit;
	private int miss;

	public static interface Accessor {
		public String getKey(String line);
	}

	public FileLinesOffsetsIndex(RandomAccessFile file, Accessor accessor,
			boolean fullPreload, int bufferSize) throws FileNotFoundException {

		this.accessor = accessor;
		this.randomAccessFile = file;

		if (fullPreload) {
			cachedLines = new HashMap<>();
			offsets = null;
		}
		else {
			cachedLines = new LRUMap<String, String>(bufferSize);
			offsets = new HashMap<>();
		}

	}

	public void build() throws IOException {
		if (!built) {

			long offset = 0;
			randomAccessFile.seek(offset);

			String line = randomAccessFile.readLine();
			while(line != null) {
				String key = accessor.getKey(line);

				if (offsets != null) {
					offsets.put(key, offset);
				}
				cachedLines.put(key, line);

				offset = randomAccessFile.getFilePointer();
				line = randomAccessFile.readLine();
			}

			built = true;
		}
	}

	public String get(String key) throws IOException {
		String cached = cachedLines.get(key);
		if (cached != null) {
			hit++;
			return cached;
		}
		miss++;

		if (offsets != null) {
			Long offset = offsets.get(key);
			if (offset != null) {
				randomAccessFile.seek(offset);
				String line = randomAccessFile.readLine();
				cachedLines.put(key, line);
				return line;
			}
		}

		return null;
	}

	public void close() throws IOException {
		randomAccessFile.close();
		if (offsets != null) {
			log.info("hit/miss ratio: {}/{}", hit, miss);
		}
	}

}
