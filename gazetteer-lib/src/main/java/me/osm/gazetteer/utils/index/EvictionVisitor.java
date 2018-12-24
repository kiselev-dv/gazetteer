package me.osm.gazetteer.utils.index;

import java.util.Map;

public interface EvictionVisitor<K, V> {
	public void onEviction(Map.Entry<K, V> entry);
}
