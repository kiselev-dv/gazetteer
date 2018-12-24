package me.osm.osmdoc.imports.osmcatalog.model;

import java.util.ArrayList;
import java.util.List;

public class CatalogItem extends MoreTagsOwner {
	
	private String name;
	private List<CatalogItem> parent = new ArrayList<>();
	private List<Tag> tags = new ArrayList<>();
	private List<Tag> excludeTags = new ArrayList<>();
	
	private List<String> type;
	private boolean poi;
	
	public String getName() {
		return name;
	}
	public CatalogItem setName(String name) {
		this.name = name;
		return this;
	}
	public List<CatalogItem> getParent() {
		return parent;
	}
	public void setParent(List<CatalogItem> parent) {
		this.parent = parent;
	}
	public List<Tag> getTags() {
		return tags;
	}
	public void setTags(List<Tag> tags) {
		this.tags = tags;
	}
	public List<Tag> getExcludeTags() {
		return excludeTags;
	}
	public void setExcludeTags(List<Tag> excludeTags) {
		this.excludeTags = excludeTags;
	}
	public List<String> getType() {
		return type;
	}
	public void setType(List<String> type) {
		this.type = type;
	}
	public boolean isPoi() {
		return poi;
	}
	public void setPoi(boolean poi) {
		this.poi = poi;
	}
	public String toString() {
		StringBuilder s = new StringBuilder();
		s.append(name).append(" (").append(tagsAsString(tags)).append(")");
		if(!excludeTags.isEmpty()) {
			s.append(" exclude (");
			s.append(tagsAsString(excludeTags));
			s.append(")");
		}
		return s.toString();
	}
	
	private String tagsAsString(List<Tag> ts) {
		StringBuilder s = new StringBuilder();
		for(Tag t : ts) {
			s.append(", ");
			s.append(t);
		}
		return s.length() > 2 ? s.substring(2) : "";
	}
}
