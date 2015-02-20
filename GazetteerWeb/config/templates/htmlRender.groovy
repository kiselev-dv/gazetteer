import me.osm.gazetteer.web.api.imp.SnapshotRender;
import groovy.text.SimpleTemplateEngine;
import groovy.json.JsonSlurper;
import me.osm.gazetteer.web.utils.OSMDocSinglton;

import java.util.Locale;

class HTMLSnapshotRender implements SnapshotRender {
	
	def engine = new SimpleTemplateEngine();
	
	def template = engine.createTemplate(new File('config/templates/feature.html'));
	
	@Override
	public void updateTemplate() {
		template = engine.createTemplate(new File('config/templates/feature.html'));
	}
	
	def osmdoc = OSMDocSinglton.get();
	def locale = Locale.forLanguageTag("ru");
	
	@Override
	public String render(String json) {
		def jsonObj = new JsonSlurper().parseText(json);
		return template.make([
			"f":jsonObj, 
			"HTML_ROOT": "/", 
			"RENDER":this, 
			"OSM_DOC": osmdoc, 
			"LOCALE": locale
		]).toString();
	}
	
	def translations = [
		"templates.feature.showOnMap": "Показать на карте", 
		"templates.feature.poiTypes": "Направления деятельности:",
		"templates.feature.address": "Адрес: ",
		"templates.feature.moreTags": "Дополнительная информация: ",
		"templates.feature.sameBuilding": "Организации в том-же здании",
		"templates.feature.sameType": "Организации тог-же типа поблизости"
	];
	
	public String tr(String key) {
		return translations[key] == null ? key : translations[key];
	}
	
	public String nameAndType(subj) {
		String r = "";
		if(subj.name != null) {
			r += subj.name + " ";
		}
		
		String types = "";
		for(typeCode in subj.poi_class) {
			types += ", " + osmdoc.facade.getTranslatedTitle(osmdoc.facade.getFeature(typeCode), locale);
		}
		
		return r + "(" + types.substring(2) + ")";
	}
	
}