package me.osm.gazetteer.web.api.imp;

public interface SitemapRender {
	
	public void pageBegin();
	public void feature(String id);
	public void pageEnd();
	
	public void indexBegin();
	public void page(int page);
	public void indexEnd();
}
