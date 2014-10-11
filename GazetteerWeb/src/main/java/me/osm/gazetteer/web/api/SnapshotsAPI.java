package me.osm.gazetteer.web.api;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import me.osm.gazetteer.web.Configuration;
import me.osm.gazetteer.web.utils.VelocityHelper;

import org.apache.commons.lang3.StringUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.elasticsearch.common.netty.buffer.ChannelBuffers;
import org.elasticsearch.common.network.MulticastChannel.Config;
import org.json.JSONObject;
import org.restexpress.Request;
import org.restexpress.Response;

public class SnapshotsAPI {
	
	
	private static final VelocityEngine ve;
	static {
		ve = new VelocityEngine();
		ve.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
		ve.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
		ve.init();
	}

	private String featureURLBAse = "/";
	
	public SnapshotsAPI(Configuration config) {
		this.featureURLBAse = config.getSiteXMLFeatureURL();
	}
	
	public void read(Request req, Response res)	{
		String parameters = StringUtils.substringAfter(req.getPath(), "_escaped_fragment_=");
		
		try	{
			
		    
			JSONObject feature = FeatureAPI.getFeature(getFeatureId(parameters), false);

			if(feature != null) {
				Template t = ve.getTemplate("velocity/feature.velocity.html");
				VelocityContext vc = new VelocityContext();
				
				vc.put("featureJSON", feature);
				vc.put("fDetailsBase", featureURLBAse);
				vc.put("u", VelocityHelper.INSTANCE);
				
				StringWriter sw = new StringWriter();
				t.merge(vc, sw);
				
				res.setContentType("text/html; charset=utf8");
				res.setBody(sw.toString());
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
