package me.osm.gazetter.join;

import java.io.PrintWriter;

import org.json.JSONObject;

public class JSONWriter {
	
	private static final JSONWriter instance = new JSONWriter();
	
	private PrintWriter writer = new PrintWriter(System.out);
	
	public static JSONWriter get() {
		return instance;
	}
	
	private JSONWriter(){
		
	}
	
	public synchronized void write(JSONObject obj) {
		writer.println(obj.toString());
	}
	
	public void close() {
		writer.close();
	}
}
