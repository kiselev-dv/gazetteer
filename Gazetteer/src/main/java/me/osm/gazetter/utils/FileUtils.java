package me.osm.gazetter.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

public class FileUtils {

	public static interface LineFilter {
		public boolean isSuitable(String s);
	}

	public static interface LineHandler {
		public void handle(String s);
	}

	public static List<String> readLines(File f) {
		return readLines(f, null);
	}

	public static void handleLines(InputStream f, LineHandler handler) {
		BufferedReader bufferedReader = null;
		try {
			bufferedReader = new BufferedReader(new InputStreamReader(f));
			
			String line = bufferedReader.readLine();
			do {
				handler.handle(line);
				line = bufferedReader.readLine();
			} while (line != null);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			if (bufferedReader != null) {
				try {
					bufferedReader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	public static void handleLines(File f, LineHandler handler) {
		try {
			handleLines(new FileInputStream(f), handler);
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	public static List<String> readLines(File f, final LineFilter filter) {

		final List<String> result = new ArrayList<>();

		handleLines(f, new LineHandler() {

			@Override
			public void handle(String s) {
				if (filter != null && filter.isSuitable(s)) {
					result.add(s);
				}
			}

		});

		return result;
	}

	public static InputStream getFileIS(String osmFilePath) throws IOException,
			FileNotFoundException {
		if (osmFilePath.endsWith("gz")) {
			return new GZIPInputStream(new FileInputStream(osmFilePath));
		}
		if (osmFilePath.endsWith("bz2")) {
			return new BZip2CompressorInputStream(new FileInputStream(
					osmFilePath));
		}
		return new FileInputStream(osmFilePath);
	}

}
