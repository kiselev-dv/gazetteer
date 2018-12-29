package me.osm.gazetteer.utils.index;

import org.apache.commons.collections4.map.LRUMap;

public class LRUCache<K, V> extends LRUMap<K, V> {

	private static final long serialVersionUID = -929108377940425736L;

	private EvictionVisitor<K, V> ev;

	public LRUCache(int cacheSize) {
		super(cacheSize);
	}

	public LRUCache(int cacheSize, EvictionVisitor<K, V> ev) {
		super(cacheSize);
		this.ev = ev;
	}

	public void setEvictionVisitor(EvictionVisitor<K, V> ev) {
		this.ev = ev;
	}

	@Override
	protected boolean removeLRU(LinkEntry<K, V> entry) {
		this.ev.onEviction(entry);
		return super.removeLRU(entry);
	}

}
