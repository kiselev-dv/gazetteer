package me.osm.gazetteer.web.api;

import groovy.lang.GroovyClassLoader;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import me.osm.gazetteer.web.Configuration;
import me.osm.gazetteer.web.GazetteerWeb;
import me.osm.gazetteer.web.api.meta.Endpoint;
import me.osm.gazetteer.web.api.renders.HTMLSitemapRender;
import me.osm.gazetteer.web.api.renders.SnapshotRender;
import me.osm.gazetteer.web.stats.APIRequest.APIRequestBuilder;
import me.osm.gazetteer.web.stats.StatWriterUtils;
import me.osm.gazetteer.web.stats.StatisticsWriter;
import me.osm.gazetteer.web.utils.OSMDocSinglton;
import me.osm.osmdoc.localization.L10n;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.domain.metadata.UriMetadata;

public class SnapshotsAPI implements DocumentedApi {
	
	private Configuration config;
	
	private static final SnapshotRender render;
	static {
		
		GroovyClassLoader gcl = new GroovyClassLoader(SnapshotsAPI.class.getClassLoader());
		try {
			gcl.addClasspath("lib");
			Class<?> clazz = gcl.parseClass(new File(GazetteerWeb.config().getSnapshotsRender()));
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
		this.config = config;
	}
	
	public void read(Request req, Response res)	{
		
		if(StringUtils.contains(req.getPath(), "update_template") ) {
			render.updateTemplate();
		}
		
		try	{
			if(StringUtils.contains(req.getPath(), "_escaped_fragment_=")) {
				String parameters = StringUtils.substringAfter(req.getPath(), "_escaped_fragment_=");
				
				APIRequestBuilder stat = StatWriterUtils.fillFromRequest(req);
				stat.api("snapshot");
				
				fillBotStats(req, stat);
				
				if(StringUtils.remove(parameters, '/').isEmpty()) {
					renderIndexSnapshot(res);
					stat.query("index");
					StatisticsWriter.write(stat.build());
					return;
				}

				String lang = "ru";
				for(String aLocation : L10n.supported) {
					if(StringUtils.contains(parameters, "/" + aLocation + "/")) {
						lang = aLocation;
					}
				}
				
				if(StringUtils.contains(parameters, "/id/")) {
					String id = StringUtils.substringAfter(parameters, "/id/");
					id = StringUtils.substringBefore(id, "/details");
					stat.resultId(id);
					
					JSONObject feature = FeatureAPI.getFeature(id, true);
					
					if(feature != null) {
						renderFeatureSnapshot(lang, res, feature);
						stat.status(200).resultLatLon(feature);
					}
					else {
						res.setResponseCode(404);
						stat.status(404);
						StatisticsWriter.write(stat.build());
						return;
					}
				}
				else {
					Map<String, String> args = parseArgs(parameters);
					
					if(args.get("index_page") != null) {
						int page = Integer.parseInt(args.get("index_page"));
						renderSitemapPage(page, res);
						return;
					}
					
					if(args.get("fid") != null) {
						JSONObject feature = FeatureAPI.getFeature(args.get("fid"), true);
						
						if(feature != null) {
							renderFeatureSnapshot(lang, res, feature);
						}
						else {
							res.setResponseCode(404);
							return;
						}
					}
				}
				
			}
			//catalog snapshot
			else if(StringUtils.contains(req.getPath(), "hierarchy/")) {
				String parameters = StringUtils.substringAfter(req.getPath(), "hierarchy/");
				String lang = StringUtils.substringBefore(parameters, "/");
				JSONObject hierarchyJSON = OSMDocSinglton.get().getFacade().getHierarchyJSON("osm-ru", Locale.forLanguageTag(lang));
				if(hierarchyJSON != null) {
					res.setContentType("text/html; charset=utf8");
					res.setBody(render.render("ru", hierarchyJSON.toString(), "hierarchy"));
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

	private void fillBotStats(Request req, APIRequestBuilder stat) {
		String uaHeader = req.getHeader("User-Agent");
		if (uaHeader != null) {
			uaHeader = uaHeader.toLowerCase();
			if(StringUtils.contains(uaHeader, "googlebot")) {
				stat.userId("googlebot");
			}
			if(StringUtils.contains(uaHeader, "yandexbot")) {
				stat.userId("yandexbot");
			}
			if(StringUtils.contains(uaHeader, "bingbot")) {
				stat.userId("bingbot");
			}
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
			res.setContentType("text/html; charset=utf8");
			res.setBody(httpSitemapRender.toString());
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}

	private void renderFeatureSnapshot(String lang, Response res, JSONObject feature)
			throws Exception {
		
		addSchema(feature);
		
		try {
			res.setContentType("text/html; charset=utf8");
			res.setBody(render.render(lang, feature.toString(), "feature"));
		}
		catch (Exception e) {
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
		
		try {
			parameters = URLDecoder.decode(parameters, "utf-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
		
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

	@Override
	public Endpoint getMeta(UriMetadata uriMetadata) {
		
		Endpoint meta = new Endpoint(uriMetadata.getPattern(), "HTML Snapshots", 
				"Generate HTML Snapshot of features for search engines crawlers. "
			  + "Feature id will be extracted from path.");
		
		return meta;
	}
}
