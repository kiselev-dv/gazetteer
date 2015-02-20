package me.osm.gazetteer.web.api.imp;

public interface SnapshotRender {
	
	public String render(String json);
	
	public void updateTemplate();
	
}
