package me.osm.gazetteer.web.api.renders;

import me.osm.gazetteer.web.Configuration;

public abstract class ASitemapRender implements SitemapRender {
	
	protected String webRoot;
	protected String hostName;
	protected String featureUrlTemplate;

	public ASitemapRender(Configuration config) {
		this.featureUrlTemplate = config.getSiteXMLFeatureURL();
		this.webRoot = config.getWebRoot();
		this.hostName = config.getHostName();
	}

	protected StringBuilder sb;
	
	@Override
	public void pageBegin() {
		sb = new StringBuilder();
	}

	@Override
	public void pageEnd() {
	}

	@Override
	public void indexBegin() {
		sb = new StringBuilder();
	}

	@Override
	public void indexEnd() {
		
	}
	
	public String toString() {
		return sb.toString();
	}

}
