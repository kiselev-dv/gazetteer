package me.osm.gazetteer.web.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public interface Ref {
	
	@JsonProperty("id")
	public String getId();
	@JsonProperty("id")
	void setId(String id);
	
	@JsonProperty("name")
	public String getName();
	@JsonProperty("name")
	void setName(String name);

	@JsonProperty("alt_names")
	public List<String> getAltNames();
	@JsonProperty("alt_names")
	void setAltNames(List<String> altNames);
	
}
