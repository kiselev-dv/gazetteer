package me.osm.osmdoc.imports.osmcatalog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class Utils {
	
	public static String getFileContent(File f) {
		
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(new FileInputStream(f)));
			String line = reader.readLine();
		
			StringBuilder r = new StringBuilder();
			
			while (line != null) {
				r.append(line).append("\n");
				line = reader.readLine();
			}
			
			return r.toString();
		} catch (Exception e) {
			e.printStackTrace();
		}
		finally{
			if(reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		return null;
		
	}
	
}
