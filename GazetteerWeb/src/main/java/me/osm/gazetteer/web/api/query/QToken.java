package me.osm.gazetteer.web.api.query;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

public class QToken {

	private String text;
	private List<String> variants;
	private boolean hasNumbers;
	private boolean numbersOnly;
	private boolean optional;
	
	public QToken(String text, List<String> variants, boolean hasNumbers, boolean numbersOnly, boolean optional) {
		this.text = text;
		this.hasNumbers = hasNumbers;
		this.numbersOnly = numbersOnly;
		this.optional = optional;
		this.variants = variants;
	}

	public boolean isHasNumbers() {
		return hasNumbers;
	}

	public boolean isNumbersOnly() {
		return numbersOnly;
	}

	public boolean isOptional() {
		return optional;
	}
	
	public List<String> getVariants() {
		return variants;
	}
	
	public boolean isFuzzied() {
		return variants != null && !variants.isEmpty();
	}

	@Override
	public String toString() {
		return text;
	}

	public String print() {
		
		if(optional) {
			return "opt(" + text + ")";
		}
		
		if(isFuzzied()) {
			return "[" + StringUtils.join(variants, "|") + "]";
		}
		
		return text;
	}
}
