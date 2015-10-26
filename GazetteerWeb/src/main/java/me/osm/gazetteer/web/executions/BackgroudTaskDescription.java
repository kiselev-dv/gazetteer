package me.osm.gazetteer.web.executions;

import java.util.Map;

public class BackgroudTaskDescription {
	
	private int id;
	private String className;
	private Map<String, Object> parameters;
	
	public String getClassName() {
		return className;
	}
	public void setClassName(String className) {
		this.className = className;
	}
	public Map<String, Object> getParameters() {
		return parameters;
	}
	public void setParameters(Map<String, Object> parameters) {
		this.parameters = parameters;
	}
	public int getId() {
		return id;
	}
	public void setId(int id) {
		this.id = id;
	}
}
