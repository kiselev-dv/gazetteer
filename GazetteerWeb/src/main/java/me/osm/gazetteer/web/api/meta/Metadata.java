package me.osm.gazetteer.web.api.meta;

import java.util.ArrayList;
import java.util.List;

public class Metadata {
	
	private List<Endpoint> endpoints = new ArrayList<>();

	public List<Endpoint> getEndpoints() {
		return endpoints;
	}

	public void setEndpoints(List<Endpoint> endpoints) {
		this.endpoints = endpoints;
	} 
	
	
}
