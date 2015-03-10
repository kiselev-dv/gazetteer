package me.osm.gazetteer.web.api.imp;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

public class QueryAnalyzer {

	private static final String tokenSeparators = ", -;.\"";
	public static final Set<String> ignore = new HashSet<String>(); 
	static {
		readOptionals();
	}
	
	@SuppressWarnings("unchecked")
	private static void readOptionals() {
		try {
			for(String option : (List<String>)IOUtils.readLines(QueryAnalyzer.class.getResourceAsStream("/optional"))) {
				if(!StringUtils.startsWith(option, "#") && !StringUtils.isEmpty(option)) {
					ignore.add(StringUtils.lowerCase(option));
				}
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public Query getQuery(String q) {
		
		if(null == q) {
			return null;
		}
		
		q = transform(q);

		q = q.toLowerCase();
		q = StringUtils.replaceChars(q, "ё", "е");
		
		String[] tokens = StringUtils.split(q, tokenSeparators);
		
		List<QToken> result = new ArrayList<QToken>(tokens.length);

		for(String t : tokens) {
			
			String withoutNumbers = StringUtils.replaceChars(t, "0123456789", "");
			
			boolean hasNumbers = withoutNumbers.length() != t.length();
			boolean numbersOnly = StringUtils.isBlank(withoutNumbers);
			boolean optional = ignore.contains(StringUtils.lowerCase(t)) 
					|| (!hasNumbers && withoutNumbers.length() < 3);
			
			result.add(new QToken(t, hasNumbers, numbersOnly, optional));
		}
		
		
		return new Query(result);
	}

	protected String transform(String q) {
		return q;
	}
	
}
