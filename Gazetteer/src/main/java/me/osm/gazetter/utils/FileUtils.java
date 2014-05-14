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

public class FileUtils {

	public static interface LineFilter {
		public boolean isSuitable(String s);
	}

	public static interface LineHandler {
		public void handle(String s);
	}

	public static List<String> readLines(File f) throws IOException {
		return readLines(f, null);
	}

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
	
	public static void handleLines(File f, LineHandler handler) throws IOException {
		try {
			handleLines(getFileIS(f), handler);
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

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
	
	public static PrintWriter getPrintwriter(File file, boolean append) throws IOException {
		
		OutputStream os = new FileOutputStream(file, append);
		if(file.getName().endsWith(".gz")) {
			os = new GZIPOutputStream(os);
		}
		
		return new PrintWriter(new OutputStreamWriter(os, "UTF8"));
	}

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
