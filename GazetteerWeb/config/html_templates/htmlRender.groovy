import groovy.text.SimpleTemplateEngine
import me.osm.gazetteer.web.api.renders.SnapshotRender
import me.osm.gazetteer.web.utils.OSMDocSinglton

import groovy.json.JsonSlurper

import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory

class HTMLSnapshotRender implements SnapshotRender {

	def log = LoggerFactory.getLogger("HTMLSnapshotRender");

	def engine = new SimpleTemplateEngine();

	def tpls = new File('config/html_templates/').list(new FilenameFilter() {
		boolean accept(File f, String name) {
			return StringUtils.endsWith(name, '.html');
		};
	});

	def osmdoc = OSMDocSinglton.get();

	def trA = [:];

	def templates = [:];

	public HTMLSnapshotRender() {
		updateTemplate();
	}

	@Override
	public void updateTemplate() {

		def base = 'config/html_templates/';

		tpls = new File(base).list(new FilenameFilter() {
			boolean accept(File f, String name) {
				return StringUtils.endsWith(name, '.html');
			};
		});

		tpls.each {
			item ->
			String key = StringUtils.remove(item, ".html");

			templates[key] = engine.createTemplate(new File(base + item));
		}
		
		for(loc in ['ru', 'en']) {
			Properties prop = new Properties();
			prop.load(new FileInputStream(
				'config/html_templates/snapshots_' + loc + '.properties'));
			trA.put(loc, prop);
		}
	}

	@Override
	public String render(String localeName, String json, String templateName) {

		def jsonObj = new JsonSlurper().parseText(json);

		def context = new context(jsonObj, localeName);

		return context.render(templateName);
	}

	class context  {

		def jsonObj;
		def localeName;

		def context (jsonObj, localeName) {
			this.jsonObj = jsonObj;
			this.localeName = localeName;
		}

		def render(templateName) {
			return templates[templateName].make([
				"f":jsonObj,
				"HTML_ROOT": "/",
				"RENDER": this,
				"OSM_DOC": osmdoc,
				"LOCALE": Locale.forLanguageTag(localeName)
			]).toString();
		}

		def tr(key) {
			def translations = trA[localeName];
			return translations.getProperty(key) == null ? key : translations.getProperty(key);
		}

		def callTemplate(name, f, subj) {

			return templates[name].make([
				"f":f,
				"subj": subj,
				"HTML_ROOT": "/",
				"RENDER": this,
				"OSM_DOC": osmdoc,
				"LOCALE": Locale.forLanguageTag(localeName)
			]).toString();
		}
	}
}
