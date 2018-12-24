package me.osm.osmdoc.read.util;

import me.osm.osmdoc.model.Trait;

public interface TraitsVisitor {
	
	public void visit(Trait t);
	
	public static TraitsVisitor VOID_VISITOR = new TraitsVisitor(){

		@Override
		public void visit(Trait t) {
			
		}
		
	};

}
