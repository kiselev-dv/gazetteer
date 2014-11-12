package me.osm.gazetteer.web.api;

import java.util.Locale;

import me.osm.gazetteer.web.utils.OSMDocSinglton;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.restexpress.Request;
import org.restexpress.Response;

public class OSMDocAPI {
	
	public JSONObject read(Request request, Response response) {
		

		String langCode = request.getHeader("lang");
		Locale lang = null;
		if(langCode != null) {
			lang = Locale.forLanguageTag(langCode);
		}

		String handler = (String) request.getParameter("handler");
		
		if(handler.equals("hierarchy")) {
			String hierarchy = request.getHeader("id");
			return OSMDocSinglton.get().getFacade().getHierarchyJSON(StringUtils.stripToNull(hierarchy), lang);
		}
		else if(handler.equals("poi-class")) {

			JSONObject classes = new JSONObject();
			for(JSONObject f : OSMDocSinglton.get().getFacade().listTranslatedFeatures(lang)) {
				classes.put(f.getString("name"), f);
			}
			
			return classes;
		}
		
		return null;
		
	}
}
