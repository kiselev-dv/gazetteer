package me.osm.gazetteer.psqlsearch.sqlquery;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

public class TSVector {
	
	private static final String OR = " | ";
	private static final String AND = " & ";

	public static String asTSVectorOR(List<String> terms, boolean prefix) {
		String join = StringUtils.join(terms, OR);
		return join + ( prefix ? ":*" : "");
	}

	public static String asTSVectorAND(List<String> terms, boolean prefix) {
		String join = StringUtils.join(terms, AND);
		return join + ( prefix ? ":*" : "");
	}

}
