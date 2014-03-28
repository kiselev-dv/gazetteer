package me.osm.gazetter.out;

import java.io.OutputStreamWriter;

import au.com.bytecode.opencsv.CSVWriter;

public class OSMRUOutConverter  implements OutConverter {
	
	private CSVWriter writer;
	
	private static final String[] header = new String[]{
		"id",
		"osm-id",
		"lon",
		"lat",
		"housenumber",
		"street",
		"place:quarter",
		"place:neighbourhood",
		"place:suburb",
		"place:allotments",
		"place:locality place:isolated_dwelling place:village place:hamlet place:city",
		"boundary:8",
		"boundary:6",
		"boundary:5",
		"boundary:4",
		"boundary:3",
		"boundary:2"
	};
	
	public OSMRUOutConverter() {
		writer = new CSVWriter(new OutputStreamWriter(System.out));
		writer.writeNext(header);
	}

	@Override
	public void handle(String s) {
		
	}

	@Override
	public void close() {
		
	}
}
