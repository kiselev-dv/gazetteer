package me.osm.gazetter.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

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
	
	public static void handleLines(File f, LineHandler handler) {
		BufferedReader bufferedReader = null;
		try {
			bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(f)));
			
			String line = bufferedReader.readLine();
			do{
				handler.handle(line);
				line = bufferedReader.readLine();
			}
			while (line != null);
		} catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			if(bufferedReader != null) {
				try {
					bufferedReader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	public static List<String> readLines(File f, final LineFilter filter) {
		
		final List<String> result = new ArrayList<>();
		
		handleLines(f, new LineHandler(){

			@Override
			public void handle(String s) {
				if(filter != null && filter.isSuitable(s)) {
					result.add(s);
				}
			}
			
		});
		
		return result;
	}

}
