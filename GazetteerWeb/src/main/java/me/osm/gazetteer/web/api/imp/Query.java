package me.osm.gazetteer.web.api.imp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class Query {
	
	private List<QToken> tokens;
	
	public Query(List<QToken> tokens) {
		this.tokens = tokens;
	}

	public Query head() {
		
		if(this.tokens.size() > 1) {
			return new Query(this.tokens.subList(0, this.tokens.size() - 1));
		}
		
		return null;
	}

	public Query tail() {
		if(tokens.size() > 0) {
			return new Query(Collections.singletonList(tokens.get(tokens.size() - 1)));
		}
		
		return null;
	}
	
	public String toString() {
		
		StringBuilder sb = new StringBuilder();

		if(tokens == null || tokens.isEmpty()) {
			return "";
		}
		
		for(QToken t : tokens) {
			sb.append(" ").append(t.toString());
		}
		
		return sb.substring(1);
		
	}
	
	public int countTokens() {
		return tokens.size();
	}
	
	public int countNumeric() {
		
		int r = 0;
		for(QToken token : tokens) {
			if(token.isHasNumbers()) {
				r++;
			}
		}
		
		return r;
	}

	public int countOptional() {
		
		int r = 0;
		for(QToken token : tokens) {
			if(token.isOptional()) {
				r++;
			}
		}
		
		return r;
	}
	
	public List<QToken> listToken() {
		return tokens;
	}

	public Query filter(Collection<String> remove) {
		List<QToken> r = new ArrayList<QToken>();
		for(QToken t : this.tokens) {
			if(!remove.contains(t.toString())) {
				r.add(t);
			}
		}
		return new Query(r);
	}

	public Query required() {
		List<QToken> r = new ArrayList<QToken>();
		for(QToken t : this.tokens) {
			if(!t.isOptional()) {
				r.add(t);
			}
		}
		return new Query(r);
	}

	public Query woNumbers() {
		List<QToken> r = new ArrayList<QToken>();
		for(QToken t : this.tokens) {
			if(!t.isHasNumbers()) {
				r.add(t);
			}
		}
		return new Query(r);
	}

	public String print() {
		
		StringBuilder sb = new StringBuilder();

		if(tokens == null || tokens.isEmpty()) {
			return "";
		}
		
		for(QToken t : tokens) {
			sb.append(" ").append(t.print());
		}
		
		return sb.substring(1);
	}
	
}
