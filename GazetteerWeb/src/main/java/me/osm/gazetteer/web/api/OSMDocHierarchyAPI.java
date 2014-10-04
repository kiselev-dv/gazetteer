package me.osm.gazetteer.web.api;

import java.util.ArrayList;
import java.util.Locale;

import me.osm.osmdoc.read.DOCFileReader;
import me.osm.osmdoc.read.DOCFolderReader;
import me.osm.osmdoc.read.DOCReader;
import me.osm.osmdoc.read.OSMDocFacade;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.restexpress.Request;
import org.restexpress.Response;

public class OSMDocHierarchyAPI {
	
	private OSMDocFacade facade;
	private DOCReader reader;

	public OSMDocHierarchyAPI(String docPath) {
		if(docPath.endsWith(".xml") || docPath.equals("jar")) {
			reader = new DOCFileReader(docPath);
		}
		else {
			reader = new DOCFolderReader(docPath);
		}
		
		facade = new OSMDocFacade(reader, new ArrayList<String>());
	}
	
	public JSONObject read(Request request, Response response) {
		
		String hierarchy = request.getHeader("hierarchy");
		String langCode = request.getHeader("lang");

		Locale lang = null;
		
		if(langCode != null) {
			lang = Locale.forLanguageTag(langCode);
		}
		
		return facade.getHierarchyJSON(StringUtils.stripToNull(hierarchy), lang);
	}
}
