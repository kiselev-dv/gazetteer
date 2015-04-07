import me.osm.gazetteer.web.api.imp.SnapshotRender;
import groovy.text.SimpleTemplateEngine;
import groovy.json.JsonSlurper;
import me.osm.gazetteer.web.utils.OSMDocSinglton;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;

import me.osm.osmdoc.model.Tag.TagValueType;

import java.util.Locale;

class HTMLSnapshotRender implements SnapshotRender {
	
	def log = LoggerFactory.getLogger("HTMLSnapshotRender");
	
	def engine = new SimpleTemplateEngine();
	
	def tpls = [
	    'config/templates/hgroup.html',
	    'config/templates/hierarchy.html',
		'config/templates/feature.html',
		'config/templates/poi-types.html',
		'config/templates/poi-more-tags.html',
		'config/templates/poi-related.html',
		'config/templates/name.html',
		'config/templates/ref.html',
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
		"templates.feature.otherNames": "Другие названия этого места: ",
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
	public String render(String json, String templateName) {
		def jsonObj = new JsonSlurper().parseText(json);
		def result = templates[templateName].make([
			"f":jsonObj, 
			"HTML_ROOT": "/", 
			"RENDER":this, 
			"OSM_DOC": osmdoc, 
			"LOCALE": locale
		]).toString();
	
		if(StringUtils.contains(result, "map?fid=AU")) {
			log.debug("template: {}, feature: {}", templateName, json);
		}
		
		return result;
	}
	
	public String tr(String key) {
		return translations[key] == null ? key : translations[key];
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