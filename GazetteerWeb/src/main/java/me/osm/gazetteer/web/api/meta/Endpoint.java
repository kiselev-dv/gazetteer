package me.osm.gazetteer.web.api.meta;

import java.util.ArrayList;
import java.util.List;

public class Endpoint {
	private String url;
	private String name;
	private String description;
	
	private List<String> httpMethods = new ArrayList<>();
	private List<Parameter> pathParameters = new ArrayList<>();
	private List<Parameter> urlParameters = new ArrayList<>();
	
	public Endpoint(String url, String name, String description) {
		super();
		this.url = url;
		this.name = name;
		this.description = description;
	}

	public String getUrl() {
		return url;
	}
	
	public void setUrl(String url) {
		this.url = url;
	}
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public String getDescription() {
		return description;
	}
	
	public void setDescription(String description) {
		this.description = description;
	}
	
	public List<Parameter> getPathParameters() {
		return pathParameters;
	}
	
	public void setPathParameters(List<Parameter> pathParameters) {
		this.pathParameters = pathParameters;
	}
	
	public List<Parameter> getUrlParameters() {
		return urlParameters;
	}

	public void setUrlParameters(List<Parameter> urlParameters) {
		this.urlParameters = urlParameters;
	}

	public List<String> getHttpMethods() {
		return httpMethods;
	}

	public void setHttpMethods(List<String> httpMethods) {
		this.httpMethods = httpMethods;
	}

}
