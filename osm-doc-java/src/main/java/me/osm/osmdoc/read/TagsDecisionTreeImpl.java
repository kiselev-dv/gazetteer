package me.osm.osmdoc.read;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;

import me.osm.osmdoc.model.Feature;
import me.osm.osmdoc.model.Tag;
import me.osm.osmdoc.model.Tags;
import me.osm.osmdoc.model.Tag.Val;

public class TagsDecisionTreeImpl implements TagsDecisionTree {

	private static final String TAG_VALUES_SEPARATOR_CHARS = ";,";
	private Map<String, Map<String, List<Feature>>> dictionary;
	private Map<Tag, Set<String>> tagValsCache = new HashMap<Tag, Set<String>>();
	
	public TagsDecisionTreeImpl(
			Map<String, Map<String, List<Feature>>> key2values) {
		dictionary = key2values;
	}

	public Set<String> getType(Map<String, String> tags) {
		
		Set<String> result = new TreeSet<String>();
		
		for(Entry<String, String> tag : tags.entrySet()) {
			Map<String, List<Feature>> values = dictionary.get(tag.getKey());
			
			if(values != null) {
				for(String value : StringUtils.split(tag.getValue(), TAG_VALUES_SEPARATOR_CHARS)) {
					value = StringUtils.strip(value);
					List<Feature> features = values.get(value);
					if(features != null) {
						for(Feature fDescription : features) {
							if(match(tags, fDescription)) {
								result.add(fDescription.getName());
							}
						}
					}
				}
			}
		}
		
		return result;
	}

	private boolean match(Map<String, String> tags, Feature fDescription) {
		
		List<Tags> synonyms = fDescription.getTags();
		
		for(Tags synonym : synonyms) {
			
			boolean matchAll = true;
			
			for(Tag fdTag : synonym.getTag()) {
				boolean exclude = fdTag.isExclude();
				boolean match = match(fdTag, tags);

				//excluded
				if(match && exclude) {
					matchAll = false;
					break;
				}
				
				//don't match any tag from object
				if(!match && !exclude) {
					matchAll = false;
					break;
				}
			}
			
			//Little bit Indian
			if(matchAll) {
				return true;
			}
		}
		
		return false;
	}

	private boolean match(Tag fdTag, Map<String, String> tags) {
		
		String tagKey = fdTag.getKey().getValue();
		String objTagsValue = tags.get(tagKey);
		
		if(objTagsValue == null) {
			return false;
		}
		
		Set<String> vals = getDescriptionTagValues(fdTag);
		for(String objTV : StringUtils.split(tags.get(tagKey), TAG_VALUES_SEPARATOR_CHARS)) {
			if(vals.contains(StringUtils.strip(objTV))) {
				return true;
			}
		}
		
		return false;
	}

	//TODO Value matching
	private Set<String> getDescriptionTagValues(Tag fdTag) {
		
		if(tagValsCache.containsKey(fdTag)) {
			return tagValsCache.get(fdTag);
		}
		
		Set<String> vals = new HashSet<String>();
		for(Val val : fdTag.getVal()) {
			vals.add(val.getValue());
		}
		
		tagValsCache.put(fdTag, vals);
		
		return vals;
	}

}
