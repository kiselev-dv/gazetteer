package me.osm.gazetteer.web.api.imp;

public class QToken {

	private String text;
	private boolean hasNumbers;
	private boolean numbersOnly;
	private boolean optional;
	
	public QToken(String text, boolean hasNumbers, boolean numbersOnly, boolean optional) {
		this.text = text;
		this.hasNumbers = hasNumbers;
		this.numbersOnly = numbersOnly;
		this.optional = optional;
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

	@Override
	public String toString() {
		return text;
	}
}
