package me.osm.gazetteer.web;

import java.util.Properties;

import org.restexpress.Format;
import org.restexpress.RestExpress;

public class Configuration extends org.restexpress.util.Environment
{
	private static final String NAME_PROPERTY = "name";
	private static final String PORT_PROPERTY = "port";
	private static final String DEFAULT_FORMAT_PROPERTY = "defaultFormat";
	private static final String SITE_FEATURE_URL = "site_xml_feature_url";

	private int port = 8080;
	private String name = "GazetterWeb";
	private String defaultFormat = Format.JSON;
	private String siteXMLFeatureURL = "/feature/{id}.html";
	private boolean serveStatic = false;
	private String root = "";
	
	private String adminPasswordHash = "1A7292E6063EFEFD527B98DDB49F0D38906378B3";
	private String poiCatalogPath;
	private String host;
	private boolean distanceScore;
	private String qAnalyzerTokenSeparators = ", -;.\"()[]№#";
	private String removeCharacters = "#?%*№@$\"\'";
	private boolean resendRequestOnFail = true;
	private int siteMapMapgeSize = 45000;
	
	@Override
	protected void fillValues(Properties p)
	{
		this.name = p.getProperty(NAME_PROPERTY, RestExpress.DEFAULT_NAME);
		this.port = Integer.parseInt(p.getProperty(PORT_PROPERTY, String.valueOf(RestExpress.DEFAULT_PORT)));
		this.defaultFormat = p.getProperty(DEFAULT_FORMAT_PROPERTY, Format.JSON);
		this.siteXMLFeatureURL = p.getProperty(SITE_FEATURE_URL, "/#!/map?fid={id}");
		this.serveStatic = "true".equals(p.getProperty("serve_static", "false"));
		this.root = p.getProperty("web_root", "");
		this.host = p.getProperty("sitemap_host_root", "");
		this.adminPasswordHash = p.getProperty("admin_password_sha1", "1A7292E6063EFEFD527B98DDB49F0D38906378B3");
		this.poiCatalogPath = p.getProperty("poi_catalog_path", "poi_catalog");
		this.distanceScore = "true".equals(p.getProperty("distance_score", "false"));
		this.qAnalyzerTokenSeparators = p.getProperty("query_token_separators", ", -;.\"()[]№#");
		this.removeCharacters = p.getProperty("query_remove_characters", "#?%*№@$\"\'");
		this.resendRequestOnFail = !"false".equals(p.getProperty("resend_request_on_fail", "true"));
		this.siteMapMapgeSize = Integer.parseInt(p.getProperty("sitemap_page_size", "45000"));
	}

	public String getDefaultFormat()
	{
		return defaultFormat;
	}

	public int getPort()
	{
		return port;
	}

	public String getName()
	{
		return name;
	}

	public String getSiteXMLFeatureURL() {
		return siteXMLFeatureURL;
	}

	public boolean isServeStatic() {
		return serveStatic;
	}

	public String getWebRoot() {
		return root;
	}

	public String getAdminPasswordHash() {
		return adminPasswordHash;
	}

	public String getPoiCatalogPath() {
		return poiCatalogPath == null ? "jar" : poiCatalogPath;
	}

	public String getHostName() {
		return host;
	}

	public boolean doDistanceScore() {
		return distanceScore;
	}

	public String getQueryAnalyzerSeparators() {
		return qAnalyzerTokenSeparators;
	}

	public String getRemoveCharacters() {
		return removeCharacters;
	}

	public boolean isReRestrict() {
		return resendRequestOnFail;
	}
	
	public int getSiteMapMapgeSize() {
		return siteMapMapgeSize;
	}
}
