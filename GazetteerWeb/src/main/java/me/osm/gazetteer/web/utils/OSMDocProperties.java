package me.osm.gazetteer.web.utils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

public class OSMDocProperties {
	
	public void load(Properties p) {
		
		setImportDefaultHierarchy(p.getProperty("import.default-hierarchy"));
		setApiDefaultHierarchy(p.getProperty("api.default-hierarchy"));
		
		String[] branches = StringUtils.split(p.getProperty("ignore.branch"), " ,;");
		if(branches != null) {
			setIgnoreBranches(new HashSet<String>(Arrays.asList(branches)));
		}

		String[] types = StringUtils.split(p.getProperty("ignore.type"), " ,;");
		if(types != null) {
			setIgnoreBranches(new HashSet<String>(Arrays.asList(types)));
		}
	}
	
	private String importDefaultHierarchy;
	
	private String apiDefaultHierarchy;
	
	private Set<String> ignoreBranches = new HashSet<>(); 

	private Set<String> ignoreTypes = new HashSet<>();

	public String getImportDefaultHierarchy() {
		return importDefaultHierarchy;
	}

	public void setImportDefaultHierarchy(String importDefaultHierarchy) {
		this.importDefaultHierarchy = importDefaultHierarchy;
	}

	public String getApiDefaultHierarchy() {
		return apiDefaultHierarchy;
	}

	public void setApiDefaultHierarchy(String apiDefaultHierarchy) {
		this.apiDefaultHierarchy = apiDefaultHierarchy;
	}

	public Set<String> getIgnoreBranches() {
		return ignoreBranches;
	}

	public void setIgnoreBranches(Set<String> ignoreBranches) {
		this.ignoreBranches = ignoreBranches;
	}

	public Set<String> getIgnoreTypes() {
		return ignoreTypes;
	}

	public void setIgnoreTypes(Set<String> ignoreTypes) {
		this.ignoreTypes = ignoreTypes;
	}
	
	public Set<String> getIgnore() {
		HashSet<String> result = new HashSet<>();
		result.addAll(getIgnoreBranches());
		result.addAll(getIgnoreTypes());
		return result;
	}
	
}
