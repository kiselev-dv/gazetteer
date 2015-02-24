package me.osm.gazetteer.web.api.imp;

import me.osm.gazetteer.web.Configuration;

import org.apache.commons.lang3.StringUtils;

public class XMLSitemapRender extends ASitemapRender {

	public XMLSitemapRender(Configuration config) {
		super(config);
	}

	@Override
	public void pageBegin() {
		super.pageBegin();
		
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\" \n" + 
				  "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" \n" + 
				  "        xsi:schemaLocation=\"http://www.sitemaps.org/schemas/sitemap/0.9 http://www.sitemaps.org/schemas/sitemap/0.9/sitemap.xsd\">\n");
	}

	@Override
	public void feature(String id) {
		String featureURL = StringUtils.replace(featureUrlTemplate, "{id}", id);
		featureURL = hostName + featureURL;
		
		sb.append("    <url>\n");
		sb.append("        <loc>").append(featureURL).append("</loc>");
		sb.append("    </url>\n");
	}
	
	@Override
	public void pageEnd() {
		super.pageEnd();
		sb.append("</urlset>");
	}
	
	@Override
	public void indexBegin() {
		super.indexBegin();
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		sb.append("<sitemapindex xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
	}
	

	@Override
	public void page(int page) {
		sb.append("    <sitemap>\n");
		sb.append("        <loc>").append(hostName).append(webRoot).append("/sitemap").append(page).append(".xml").append("</loc>");
		sb.append("    </sitemap>\n");
	}
	
	@Override
	public void indexEnd() {
		super.indexEnd();
		sb.append("</sitemapindex>");
	}

}
