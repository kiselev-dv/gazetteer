import me.osm.gazetteer.web.api.imp.SnapshotRender;
import groovy.text.SimpleTemplateEngine;
import groovy.json.JsonSlurper;
import me.osm.gazetteer.web.utils.OSMDocSinglton;
import org.apache.commons.lang3.StringUtils;
import me.osm.osmdoc.model.Tag.TagValueType;

import java.util.Locale;

class HTMLSnapshotRender implements SnapshotRender {
	
	def engine = new SimpleTemplateEngine();
	
	def tpls = [
		'config/templates/feature.html',
		'config/templates/poi-types.html',
		'config/templates/poi-more-tags.html',
		'config/templates/poi-related.html',
		'config/templates/name.html',
		'config/templates/address.html'
	];

	def osmdoc = OSMDocSinglton.get();
	def locale = Locale.forLanguageTag("ru");

	def translations = [
		"templates.feature.showOnMap": "Показать на карте",
		"templates.feature.poiTypes": "Направления деятельности:",
		"templates.feature.address": "Адрес: ",
		"templates.feature.building": "Здание",
		"templates.feature.moreTags": "Дополнительная информация: ",
		"templates.feature.sameBuilding": "Организации в том-же здании",
		"templates.feature.sameType": "Организации того-же типа поблизости"
	];
	
	def templates = [:];

	public HTMLSnapshotRender() {
		updateTemplate();
	}
	
	@Override
	public void updateTemplate() {
		tpls.each { item ->
			String[] s = StringUtils.split(item, "/");
			String key = StringUtils.remove(s[s.length - 1], ".html"); 
			
			templates[key] = engine.createTemplate(new File(item));
		}
	}
	
	@Override
	public String render(String json) {
		def jsonObj = new JsonSlurper().parseText(json);
		return templates['feature'].make([
			"f":jsonObj, 
			"HTML_ROOT": "/", 
			"RENDER":this, 
			"OSM_DOC": osmdoc, 
			"LOCALE": locale
		]).toString();
	}
	
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
	
	public String callTemplate(name, f, subj) {
		return templates[name].make([
			"f":f,
			"subj": subj,
			"HTML_ROOT": "/",
			"RENDER":this,
			"OSM_DOC": osmdoc,
			"LOCALE": locale
		]).toString();
	}
	
}