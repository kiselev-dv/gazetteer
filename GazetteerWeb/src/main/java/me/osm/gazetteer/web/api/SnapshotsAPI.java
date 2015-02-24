package me.osm.gazetteer.web.api;

import groovy.lang.GroovyClassLoader;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

import me.osm.gazetteer.web.Configuration;
import me.osm.gazetteer.web.api.imp.HTMLSitemapRender;
import me.osm.gazetteer.web.api.imp.SnapshotRender;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.restexpress.Request;
import org.restexpress.Response;

public class SnapshotsAPI {
	
	private String featureURLBase = "/";

	private Configuration config;
	
	private static final SnapshotRender render;
	static {
		
		GroovyClassLoader gcl = new GroovyClassLoader(SnapshotsAPI.class.getClassLoader());
		try {
			gcl.addClasspath("lib");
			Class<?> clazz = gcl.parseClass(new File("config/templates/htmlRender.groovy"));
			Object aScript = clazz.newInstance();
			
			if(aScript instanceof SnapshotRender) {
				render = (SnapshotRender) aScript;
			}
			else {
				render = null;
			}
			
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		finally {
			try {
				gcl.close();
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}
	
	public SnapshotsAPI(Configuration config) {
		this.featureURLBase = config.getSiteXMLFeatureURL();
		this.config = config;
	}
	
	public void read(Request req, Response res)	{
		
		if(StringUtils.contains(req.getPath(), "update_template") ) {
			render.updateTemplate();
		}
		
		String parameters = StringUtils.substringAfter(req.getPath(), "_escaped_fragment_=");
		Map<String, String> args = parseArgs(parameters);
		
		try	{
			//index page snapshot
			if(parameters.isEmpty()) {
				renderIndexSnapshot(res);
			}
			else if(args.get("index_page") != null) {
				int page = Integer.parseInt(args.get("index_page"));
				renderSitemapPage(page, res);
			}
			//feature snapshot
			else {
				JSONObject feature = FeatureAPI.getFeature(args.get("fid"), true);
				
				if(feature != null) {
					renderFeatureSnapshot(res, feature);
				}
				else {
					res.setResponseCode(404);
				}
			}
		}
		catch (Exception e) {
			res.setResponseCode(500);
			res.setException(e);
			e.printStackTrace();
		} 
	}

	private void renderSitemapPage(int page, Response res) {
		res.setContentType("text/html; charset=utf8");
		HTMLSitemapRender httpSitemapRender = new HTMLSitemapRender(config);
		try {
			Sitemap.renderPage(page, httpSitemapRender);
			res.setBody(httpSitemapRender.toString());
			
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}

	private void renderIndexSnapshot(Response res) {
		res.setContentType("text/html; charset=utf8");
		HTMLSitemapRender httpSitemapRender = new HTMLSitemapRender(config);
		try {
			Sitemap.renderIndex(httpSitemapRender);
			res.setBody(httpSitemapRender.toString());
			
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}

	private void renderFeatureSnapshot(Response res, JSONObject feature)
			throws Exception {
		
		addSchema(feature);
		
		try {
			res.setContentType("text/html; charset=utf8");
			res.setBody(render.render(feature.toString()));
		}
		catch (Exception e) {
			render.updateTemplate();
			throw e;
		}
	}

	private void addSchema(JSONObject feature) {
		if("poipnt".equals(feature.optString("type"))) {
			feature.put("itemtype", "http://schema.org/LocalBusiness");
		}
		else if("adrpnt".equals(feature.optString("type"))) {
			feature.put("itemtype", "http://schema.org/Residence");
		}
		else if("admbnd".equals(feature.optString("type"))) {
			feature.put("itemtype", "http://schema.org/AdministrativeArea");
		}
		else {
			feature.put("itemtype", "http://schema.org/Place");
		}
	}

	private Map<String, String> parseArgs(String parameters) {
		Map<String, String> res = new HashMap<String, String>();
		
		String p = StringUtils.substringAfter(parameters, "?");
		String[] pairs = StringUtils.split(p, '&');
		for(String pair : pairs) {
			String[] parr = StringUtils.split(pair, '=');
			if(parr.length == 2) {
				res.put(parr[0], parr[1]);
			}
		}
		return res;
	}
}
