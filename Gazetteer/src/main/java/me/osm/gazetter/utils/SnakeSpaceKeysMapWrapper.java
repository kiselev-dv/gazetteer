package me.osm.gazetter.utils;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

public class SnakeSpaceKeysMapWrapper implements Map<String, Object> {

	private Map<String, Object> map;

	public SnakeSpaceKeysMapWrapper(Map<String, Object> map) {
		this.map = map;
	}
	
	@Override
	public void clear() {
		this.map.clear();
	}

	@Override
	public boolean containsKey(Object key) {
		if(this.map.containsKey(key)) {
			return true;
		}
		
		return this.map.containsKey(reformatKey(key));
	}

	@Override
	public boolean containsValue(Object val) {
		return this.map.containsValue(val);
	}

	@Override
	public Set<Entry<String, Object>> entrySet() {
		return this.map.entrySet();
	}

	@Override
	public Object get(Object key) {
		if(this.map.containsKey(key)) {
			return this.map.get(key);
		}
		
		return this.map.get(reformatKey(key));
	}

	@Override
	public boolean isEmpty() {
		return this.map.isEmpty();
	}

	@Override
	public Set<String> keySet() {
		return this.map.keySet();
	}

	@Override
	public Object put(String key, Object value) {
		return this.map.put(key, value);
	}

	@Override
	public void putAll(Map<? extends String, ? extends Object> m) {
		this.map.putAll(m);
	}

	@Override
	public Object remove(Object key) {
		return this.map.remove(key);
	}

	@Override
	public int size() {
		return this.map.size();
	}

	@Override
	public Collection<Object> values() {
		return this.map.values();
	}

	private String reformatKey(Object keyobj) {
		String key = (String)keyobj;
		
		key = StringUtils.removeStart(key, "--");
		key = StringUtils.removeStart(key, "-");
		
		return StringUtils.replaceChars(key, '-', '_');
	}

}
