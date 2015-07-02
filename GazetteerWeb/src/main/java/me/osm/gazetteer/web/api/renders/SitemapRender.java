package me.osm.gazetteer.web.api.renders;

import org.json.JSONObject;

public interface SitemapRender {
	
	public void pageBegin();
	public void feature(String id, JSONObject obj);
	public void pageEnd();
	
	public void indexBegin();
	public void page(int page);
	public void indexEnd();
}
