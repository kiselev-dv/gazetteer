package me.osm.gazetteer.web;

import java.util.Properties;

import org.restexpress.Format;
import org.restexpress.RestExpress;

public class Configuration
extends org.restexpress.util.Environment
{
	private static final String NAME_PROPERTY = "name";
	private static final String PORT_PROPERTY = "port";
	private static final String DEFAULT_FORMAT_PROPERTY = "defaultFormat";
	private static final String SITE_FEATURE_URL = "site_xml_feature_url";

	private int port = 8080;
	private String name = "GazetterWeb";
	private String defaultFormat = Format.JSON;
	private String siteXMLFeatureURL = "/feature/{id}.html";
	private boolean seveStatic = false;
	
	@Override
	protected void fillValues(Properties p)
	{
		this.name = p.getProperty(NAME_PROPERTY, RestExpress.DEFAULT_NAME);
		this.port = Integer.parseInt(p.getProperty(PORT_PROPERTY, String.valueOf(RestExpress.DEFAULT_PORT)));
		this.defaultFormat = p.getProperty(DEFAULT_FORMAT_PROPERTY, Format.JSON);
		this.siteXMLFeatureURL = p.getProperty(SITE_FEATURE_URL, "/feature/{id}.html");
		this.seveStatic = "true".equals(p.getProperty("seve_static", "false"));
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

	public boolean isSeveStatic() {
		return seveStatic;
	}
	
}
