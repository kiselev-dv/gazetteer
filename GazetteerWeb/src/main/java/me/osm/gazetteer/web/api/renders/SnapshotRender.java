package me.osm.gazetteer.web.api.renders;

public interface SnapshotRender {
	
	public String render(String json, String templateName);
	
	public void updateTemplate();
	
}
