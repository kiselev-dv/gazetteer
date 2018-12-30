package me.osm.osmdoc.read;

import java.util.Map;
import java.util.Set;

/**
 * Decision tree.
 * Decide do we support this type of features and find feature type
 * */
public interface TagsDecisionTree {
	
	/**
	 * Find feature type by it,s tags.
	 * @param tags - feature tags
	 * @returns feature types or <code>null</code> if this kind of features is not supported
	 * */
	public Set<String> getType(Map<String, String> tags);
	
}
