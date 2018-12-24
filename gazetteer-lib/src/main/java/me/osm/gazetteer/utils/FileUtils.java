package me.osm.gazetteer.utils;

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
import java.text.DecimalFormat;
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
	 * Filter for readLines routine.
	 *
	 * Allows to filter lines during the iteration over
	 * a list of lines from file.
	 *
	 * @see FileUtils.readLines(File, LineFilter)
	 * */
	public static interface LineFilter {

		/**
		 * If line isSuitable - it will be passed to LineHandler
		 *
		 * @param s - readed line
		 * */
		public boolean isSuitable(String s);
	}

	/**
	 * Handles lines in file during the iteration over it.
	 *
	 * @see FileUtils.handleLines(InputStream, LineHandler)
	 * */
	public static interface LineHandler {

		/**
		 * @param s - readed line
		 * */
		public void handle(String s);
	}

	/**
	 * Read file line by line into list of strings
	 *
	 * Lines will be decoded as UTF-8 Strings.
	 * If file ends with .gz or .bz2 - it will be red with decompression
	 *
	 * @param f - File to be red.
	 * @returns List of Strings
	 * */
	public static List<String> readLines(File f) throws IOException {
		return readLines(f, null);
	}

	/**
	 * Read InputStream line by line, and pass lines without storing
	 * to the LineHandler.
	 *
	 * @param f - input stream to read from
	 * @param handler - lines handler callback
	 * */
	public static void handleLines(InputStream f, LineHandler handler) {
		BufferedReader bufferedReader = null;
		try {
			bufferedReader = new BufferedReader(new InputStreamReader(f, "UTF8"));

			String line = bufferedReader.readLine();
			do {
				if(line != null) {
					handler.handle(line);
				}
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
	 *
	 * @param f - file to read
	 * @param handler - callback interface
	 * */
	public static void handleLines(File f, LineHandler handler) throws IOException {
		try {
			handleLines(getFileIS(f), handler);
		} catch (FileNotFoundException e) {
			throw new RuntimeException("Failed to read file " + f.getName(), e);
		}
	}

	/**
	 * Read all lines in file into the List, with lines filtration.
	 *
	 * If file ends with .gz or .bz2 - it will be readed with decompression
	 *
	 * @param f - file to read
	 * @param filter - lines filter (will be ignored if it is null)
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
	 *
	 * @param osmFilePath - file to read
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
	public static PrintWriter getPrintWriter(File file, boolean append) throws IOException {

		if(file.getName().endsWith(".gz") && file.exists() && append) {
			throw new IllegalArgumentException("Can't append to gzipped file");
		}

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
	 *
	 * @param file to try
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
	 *
	 * @param stripeF - file to write to
	 * @param lines - lines to be written
	 * */
	public static void writeLines(File stripeF, List<String> lines) throws IOException {
		writeLines(stripeF, lines, false);
	}

	/**
	 * Write lines into file.
	 * If file name ends with .gz - file will be compressed
	 *
	 * @param stripeF - file to write to
	 * @param lines - lines to be written
	 * @param append - append or overwrite exists file
	 * */
	public static void writeLines(File stripeF, List<String> lines, boolean append) throws IOException {
		PrintWriter printwriter = null;
		try {
			printwriter = getPrintWriter(stripeF, append);
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

	/**
	 * Creates PrintWriter for file.
	 *
	 * Wrap file with GZipOutput stream if file name ends with .gz
	 *
	 * In case of append is true, and file is *.gz
	 * rewrites data into file to append via GZipOutputStream correctly.
	 *
	 * @param file file to write to. New file will be created if provided doesn't exists.
	 * @param append append to exists file or override it if append is false
	 *
	 * */
	public static PrintWriter getPrintWriterWithGZAppendTrick(File file, boolean append) throws IOException {

		/*
		 * There are JZlib library, which theoretically allows to append into
		 * exists gzip file. But I haven't give it a try yet.
		 *
		 * So code down below isn't an optimal solution,
		 * especially for large files.
		 */

		if(!file.getName().endsWith(".gz") || !append || !file.exists()) {
			return getPrintWriter(file, append);
		}

		//rename old
		File tmp = new File(file.getAbsolutePath() + ".t.gz");
		file.renameTo(tmp);

		//create new
		file.createNewFile();

		try {
			final PrintWriter writer = getPrintWriter(file, false);
			handleLines(tmp, new LineHandler() {

				@Override
				public void handle(String s) {
					writer.println(s);
				}

			});

			//delete temp file
			tmp.delete();

			return writer;
		}
		catch (IOException e) {
			throw new IOException("Failed to append to " + file.toString(), e);
		}
	}

	/**
	 * Format size in bytes in human readable form
	 * */
	public static String readableFileSize(long size) {
	    if(size <= 0) return "0";
	    final String[] units = new String[] { "B", "kB", "MB", "GB", "TB" };
	    int digitGroups = (int) (Math.log10(size)/Math.log10(1024));
	    return new DecimalFormat("#,##0.#").format(size/Math.pow(1024, digitGroups)) + " " + units[digitGroups];
	}

}
