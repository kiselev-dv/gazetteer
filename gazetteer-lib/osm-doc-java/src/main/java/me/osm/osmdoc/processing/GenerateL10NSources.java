package me.osm.osmdoc.processing;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GenerateL10NSources {
	
	public static void main(String[] args) {
		
		List<String> tail = new ArrayList<>(Arrays.asList(args));
		tail.remove(0);
		
		try {
			mergeAndWrite(new File(args[0]), tail);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void mergeAndWrite(File outFile, List<String> links) throws MalformedURLException, IOException {
		PrintWriter writer = new PrintWriter(outFile);
		
		for(String link : links) {
			BufferedReader reader = new BufferedReader(new InputStreamReader(new URL(link).openStream()));
			
			String line = reader.readLine();
			while (line != null) {
				if(!line.isEmpty() && line.charAt(0) != '#') {
					writer.println(line);
				}
				
				line = reader.readLine();
			}
			
			reader.close();
		}
		
		writer.flush();
		writer.close();
	}
	
}
