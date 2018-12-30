package me.osm.osmdoc.read.util;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import me.osm.osmdoc.model.Feature;
import me.osm.osmdoc.read.OSMDocFacade;
import me.osm.osmdoc.model.Trait;

public class TraitsParenFirstNavigator {

	private OSMDocFacade facade;

	public TraitsParenFirstNavigator(OSMDocFacade facade) {
		this.facade = facade;
	}
	
	public void visit(String feature, TraitsVisitor v, Set<String> visited) {
		visit(facade.getFeature(feature), v, visited);
	}
	
	public void visit(Feature feature, TraitsVisitor v, Set<String> visited) {
		List<Feature.Trait> traits = feature.getTrait();
		
		if(traits != null) {
			for(Feature.Trait traitRef : traits) {
				visit(facade.getTraitByRef(traitRef), v, visited);
			}
		}
	}
	
	public void visit(Trait trait, TraitsVisitor v, Set<String> visited) {
		
		if(trait != null) {
			List<String> te = trait.getExtends();
			
			// Visit all parents first
			if(te != null && !te.isEmpty()) {
				for(String textends : te) {
					if(!visited.contains(textends)) {
						Trait parentTrait = facade.getTraitByName(textends);
						visit(parentTrait, v, visited);
					}
				}
			}
			
			// Then visit curent trait
			if(visited.add(trait.getName())) {
				v.visit(trait);
			}
		}
	}
	
}
