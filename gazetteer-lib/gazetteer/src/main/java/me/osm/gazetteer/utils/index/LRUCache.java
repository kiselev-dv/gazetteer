package me.osm.gazetteer.utils.index;

import java.util.LinkedHashMap;
import java.util.Map;

public class LRUCache<K, V> extends LinkedHashMap<K, V> {

	private static final long serialVersionUID = -929108377940425736L;

	private int cacheSize;

	private EvictionVisitor<K, V> ev;

	public LRUCache(int cacheSize) {
		super(16, 0.75f, true);
		this.cacheSize = cacheSize;
	}

	public LRUCache(int cacheSize, EvictionVisitor<K, V> ev) {
		super(16, 0.75f, true);
		this.cacheSize = cacheSize;
		this.ev = ev;
	}

	protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {

		if (size() >= cacheSize && this.ev != null) {
			this.ev.onEviction(eldest);
		}

		return size() >= cacheSize;
	}

	public void setEvictionVisitor(EvictionVisitor<K, V> ev) {
		this.ev = ev;
	}

}
