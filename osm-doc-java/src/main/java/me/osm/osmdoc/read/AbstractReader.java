package me.osm.osmdoc.read;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import me.osm.osmdoc.model.Feature;
import me.osm.osmdoc.model.Fref;
import me.osm.osmdoc.model.Group;
import me.osm.osmdoc.model.Hierarchy;

public abstract class AbstractReader implements DOCReader {

	protected Collection<? extends Feature> getHierarcyBranch(String branch,
			Set<String> excluded, Hierarchy hierarchy, Map<String, Feature> feature2Name) {
		
		for(Fref fref : hierarchy.getFref()) {
			
			String ref = fref.getRef();
			if(ref.equals(branch)) {
				excluded.add(ref);
				break;
			}
		}

		for(Group g : hierarchy.getGroup()) {
			traverseGroup(branch, g, excluded, g.getName().equals(branch));
		}
		
		Set<Feature> result = new HashSet<Feature>();
		for(String ex : excluded) {
			Feature feature = feature2Name.get(ex);
			result.add(feature);
		}
		
		
		return result;
	}

	protected void traverseGroup(String branch, Group g, Set<String> excluded, boolean add) {
		
		if(add) {
			for(Fref fref : g.getFref()) {
				excluded.add(fref.getRef());
			}
			
			for(Group cg : g.getGroup()) {
				traverseGroup(branch, cg, excluded, true);
			}
		}
		else {
			for(Fref fref : g.getFref()) {
				if(fref.getRef().equals(branch)) {
					excluded.add(fref.getRef());
					return;
				}
			}
			
			for(Group cg : g.getGroup()) {
				traverseGroup(branch, cg, excluded, cg.getName().equals(branch));
			}
		}
	}
	
}
