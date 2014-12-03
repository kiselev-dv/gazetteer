package me.osm.gazetter.join.out_handlers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

public class HandlerOptions {
	
	private Map<String, List<String>> vals = new HashMap<String, List<String>>();
	
	private HandlerOptions() {
		
	}
	
	public String getString(String key, String def) {
		List<String> list = vals.get(key);
		if(list == null) {
			return def;
		}
		return list.get(0);
	}
	
	public List<String> getList(String key, List<String> def) {
		if(vals.get(key) == null) {
			return def;
		}
		return vals.get(key);
	}
	
	public boolean has (String key) {
		return vals.containsKey(key);
	}

	/**
	 * @returns missed if there is no <b>key</b> parameter
	 * @returns def if <b>key</b> appears without any particular value
	 * */
	public boolean getFlag (String key, Boolean def, Boolean missed) {
		if(vals.containsKey(key)) {
			if(vals.get(key).size() == 0) {
				return def;
			}
			return vals.get(key).iterator().next().equals("true");
		}
		
		return missed;
	}

	public static HandlerOptions parse(List<String> options, List<String> argNames) {
		
		HandlerOptions result = new HandlerOptions();
		
		List<String> vals = null;
		List<String> positional = new ArrayList<String>();
		
		String asOneString = StringUtils.join(options, " ");
		options = Arrays.asList(StringUtils.split(asOneString, " ="));
		
		for(String o : options ) {
			
			if(argNames.contains(o)) {
				vals = new ArrayList<String>();
				result.vals.put(o, vals);
			}
			else if (vals != null) {
				vals.add(o);
			}
			else {
				positional.add(o);
			}
		}
		
		if(!positional.isEmpty()) {
			result.vals.put(null, positional);
		}
		
		return result;
	}
	
}
