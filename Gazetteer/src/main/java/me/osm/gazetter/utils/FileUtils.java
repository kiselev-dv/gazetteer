package me.osm.gazetter.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

/**
 * File reading and writing utilities
 * */
public class FileUtils {

	/**
	 * @see me.osm.gazetter.utils.FileUtils.readLines(File, LineFilter)
	 * */
	public static interface LineFilter {
		
		/**
		 * If line isSuitable - it will be passed to LineHandler
		 * */
		public boolean isSuitable(String s);
	}

	/**
	 * @see me.osm.gazetter.utils.FileUtils.handleLines(InputStream, LineHandler)
	 * */
	public static interface LineHandler {
		public void handle(String s);
	}

	/**
	 * Read file line by line into list of strings
	 * */
	public static List<String> readLines(File f) throws IOException {
		return readLines(f, null);
	}

	/**
	 * Read InputStream line by line, and pass lines without storing 
	 * to the LineHandler. 
	 * */
	public static void handleLines(InputStream f, LineHandler handler) {
		BufferedReader bufferedReader = null;
		try {
			bufferedReader = new BufferedReader(new InputStreamReader(f, "UTF8"));
			
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
					throw new RuntimeException(e);
				}
			}
		}
	}
	
	/**
	 * Read file line by line, and pass lines without storing 
	 * to the LineHandler.
	 * <p>
	 * If file ends with .gz or .bz2 - it will be readed with decompression
	 * */
	public static void handleLines(File f, LineHandler handler) throws IOException {
		try {
			handleLines(getFileIS(f), handler);
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Read all lines in file into the List, with lines filtration. 
	 * */
	public static List<String> readLines(File f, final LineFilter filter) throws IOException {

		final List<String> result = new ArrayList<>();

		handleLines(f, new LineHandler() {

			@Override
			public void handle(String s) {
				if (filter == null || filter.isSuitable(s)) {
					result.add(s);
				}
			}

		});

		return result;
	}

	/**
	 * Returns InputStream for file.
	 * <p>
	 * If file name ends with .gz or bz2 stream will be wrapped into
	 * GZIPInputStream or BZip2CompressorInputStream accordingly.
	 * */
	public static InputStream getFileIS(File osmFilePath) throws IOException,
			FileNotFoundException {
		if (osmFilePath.getName().endsWith(".gz")) {
			return new GZIPInputStream(new FileInputStream(osmFilePath));
		}
		if (osmFilePath.getName().endsWith(".bz2")) {
			return new BZip2CompressorInputStream(new FileInputStream(
					osmFilePath));
		}
		return new FileInputStream(osmFilePath);
	}
	
	/**
	 * Return print writer for file with UTF8 encoding.
	 * <p>
	 * If filename ends with .gz - file will be compressed 
	 * 
	 * @param file - file to write into
	 * @param append - append or overwrite file content
	 * */
	public static PrintWriter getPrintwriter(File file, boolean append) throws IOException {
		
		OutputStream os = new FileOutputStream(file, append);
		if(file.getName().endsWith(".gz")) {
			os = new GZIPOutputStream(os);
		}
		
		return new PrintWriter(new OutputStreamWriter(os, "UTF8"));
	}

	/**
	 * Try to find exists file with or without .gz name suffix.
	 * <p>
	 * If none of them doesn't exists, returns original file.
	 * */
	public static File withGz(File file) {
		if(file.exists()) {
			return file;
		}
		
		File newF = null;
		if(file.getName().endsWith(".gz")) {
			newF = new File(file.getPath().replace(".gz", ""));
		}
		else {
			newF = new File(file.getPath() + ".gz");
		}
		
		if(newF.exists()) {
			return newF;
		}
		
		return file;
	}

	/**
	 * Write lines into file.
	 * If file name ends with .gz - file will be compressed
	 * */
	public static void writeLines(File stripeF, List<String> lines) throws IOException {
		PrintWriter printwriter = null;
		try {
			printwriter = getPrintwriter(stripeF, false);
			for(String line : lines) {
				printwriter.println(line);
			}
		}
		finally {
			if(printwriter != null) {
				printwriter.flush();
				printwriter.close();
			}
		}
		
	}

}
