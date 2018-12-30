package me.osm.osmdoc.imports.osmcatalog.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class  MoreTagsOwner {
	
	private List<TagDescriptor> moreTags = new ArrayList<>();
	
	public List<TagDescriptor> getMoreTags() {
		return moreTags;
	}
	
	public void setMoreTags(List<TagDescriptor> moreTags) {
		this.moreTags = moreTags;
		for(TagDescriptor td : moreTags) {
			td.setOwner(this);
		}
	}

	public void mergeMoreTags(List<TagDescriptor> moreTags) {
		Set<String> tdNames = new HashSet<>();
		for(TagDescriptor td : this.moreTags) {
			tdNames.add(td.getOsmTagName());
		}
		
		for(TagDescriptor td : moreTags) {
			if(tdNames.add(td.getOsmTagName())) {
				this.moreTags.add(0, td);
			}
		}
	}
}
