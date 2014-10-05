package me.osm.gazetteer.web.utils;

import java.util.ArrayList;

import me.osm.osmdoc.read.DOCFileReader;
import me.osm.osmdoc.read.DOCFolderReader;
import me.osm.osmdoc.read.DOCReader;
import me.osm.osmdoc.read.OSMDocFacade;

public class OSMDocSinglton {
	
	private static volatile OSMDocSinglton instance;
	
	private OSMDocFacade facade;
	private DOCReader reader;
	
	private OSMDocSinglton(){
		
	};

	private OSMDocSinglton(String docPath) {
		
		if(docPath.endsWith(".xml") || docPath.equals("jar")) {
			reader = new DOCFileReader(docPath);
		}
		else {
			reader = new DOCFolderReader(docPath);
		}
		
		facade = new OSMDocFacade(reader, new ArrayList<String>());
	};
	
	public static void initialize(String docPath) {
		if(instance == null) {
			synchronized (OSMDocSinglton.class) {
				if(instance == null) {
					instance = new OSMDocSinglton(docPath);
				}
			}
		}
	}

	public static OSMDocSinglton get() {
		return instance;
	}

	public OSMDocFacade getFacade() {
		return facade;
	}

	public DOCReader getReader() {
		return reader;
	}
	
}
