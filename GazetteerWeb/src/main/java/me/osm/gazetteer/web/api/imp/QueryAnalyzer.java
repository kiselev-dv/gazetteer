package me.osm.gazetteer.web.api.imp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

public class QueryAnalyzer {

	private String tokenSeparators = ", -;.\"";
	public Set<String> ignore; 
	
	public QueryAnalyzer() {
		try {
			ignore = new HashSet<String>(IOUtils.readLines(getClass().getResourceAsStream("/optional")));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public Query getQuery(String q) {
		
		if(null == q) {
			return null;
		}

		q = q.toLowerCase();
		
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
	
}
