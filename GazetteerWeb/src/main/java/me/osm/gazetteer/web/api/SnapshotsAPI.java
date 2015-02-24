package me.osm.gazetteer.web.api;

import groovy.lang.GroovyClassLoader;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import me.osm.gazetteer.web.Configuration;
import me.osm.gazetteer.web.api.imp.SnapshotRender;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.restexpress.Request;
import org.restexpress.Response;

public class SnapshotsAPI {
	
	private String featureURLBase = "/";
	
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
	}
	
	public void read(Request req, Response res)	{
		
		if(StringUtils.contains(req.getPath(), "update_template") ) {
			render.updateTemplate();
		}
		
		String parameters = StringUtils.substringAfter(req.getPath(), "_escaped_fragment_=");
		
		try	{
			
			JSONObject feature = FeatureAPI.getFeature(getFeatureId(parameters), true);

			if(feature != null) {
				
				addSchema(feature);
				
				try {
					String body = render.render(feature.toString());
					res.setContentType("text/html; charset=utf8");
					res.setBody(body);
				}
				catch (Exception e) {
					render.updateTemplate();
					throw e;
				}
			}
			else {
				res.setResponseCode(404);
			}
		}
		catch (Exception e) {
			res.setResponseCode(500);
			res.setException(e);
			e.printStackTrace();
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

	private String getFeatureId(String parameters) {
		
		Map<String, String> res = new HashMap<String, String>();
		
		String p = StringUtils.substringAfter(parameters, "?");
		String[] pairs = StringUtils.split(p, '&');
		for(String pair : pairs) {
			String[] parr = StringUtils.split(pair, '=');
			if(parr.length == 2) {
				res.put(parr[0], parr[1]);
			}
		}
		
		return res.get("fid");
	}
}
