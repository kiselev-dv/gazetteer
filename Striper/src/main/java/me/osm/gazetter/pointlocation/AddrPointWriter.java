package me.osm.gazetter.pointlocation;

import java.io.PrintWriter;

import org.json.JSONObject;

public class AddrPointWriter {
	
	private static final AddrPointWriter instance = new AddrPointWriter();
	
	private PrintWriter writer = new PrintWriter(System.out);
	
	public static AddrPointWriter get() {
		return instance;
	}
	
	private AddrPointWriter(){
		
	}
	
	public synchronized void write(JSONObject obj) {
		writer.write(obj.toString());
	}
	
	public void close() {
		writer.close();
	}
}
