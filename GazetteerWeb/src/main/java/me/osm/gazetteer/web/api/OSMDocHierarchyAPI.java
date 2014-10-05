package me.osm.gazetteer.web.api;

import java.util.Locale;

import me.osm.gazetteer.web.utils.OSMDocSinglton;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.restexpress.Request;
import org.restexpress.Response;

public class OSMDocHierarchyAPI {
	
	public JSONObject read(Request request, Response response) {
		
		String hierarchy = request.getHeader("hierarchy");
		String langCode = request.getHeader("lang");

		Locale lang = null;
		
		if(langCode != null) {
			lang = Locale.forLanguageTag(langCode);
		}
		
		return OSMDocSinglton.get().getFacade().getHierarchyJSON(StringUtils.stripToNull(hierarchy), lang);
	}
}
