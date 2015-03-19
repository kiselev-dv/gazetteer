package me.osm.gazetteer.web.api.imp;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.osm.gazetteer.web.Main;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueryAnalyzer {
	
	private static final Logger log = LoggerFactory.getLogger(QueryAnalyzer.class);

	private static final String tokenSeparators = Main.config().getQueryAnalyzerSeparators();
	private static final String removeChars = Main.config().getRemoveCharacters();
	
	public static final Set<String> optionals = new HashSet<String>(); 
	public static Pattern optRegexp = null;
	static {
		readOptionals();
	}
	
	@SuppressWarnings("unchecked")
	private static void readOptionals() {
		try {
			Set<String> patterns = new HashSet<>();
			for(String option : (List<String>)IOUtils.readLines(QueryAnalyzer.class.getResourceAsStream("/optional"))) {
				if(!StringUtils.startsWith(option, "#") && !StringUtils.isEmpty(option)) {
					if(StringUtils.startsWith(option, "~")) {
						patterns.add(StringUtils.substringAfter(option, "~"));
					}
					else {
						optionals.add(StringUtils.lowerCase(option));
					}
				}
			}
			
			if(!patterns.isEmpty()) {
				List<String> t = new ArrayList<>(patterns.size());
				for(String s : patterns) {
					t.add("(" + s + ")");
				}
				
				optRegexp = Pattern.compile(StringUtils.join(t, "|"));
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	//TODO: сделать настраиваемым
	public Query getQuery(String q) {
		
		if(null == q) {
			return null;
		}
		
		q = transform(q);
		
		q = StringUtils.replaceChars(q, removeChars, null);

		q = q.toLowerCase();
		q = StringUtils.replaceChars(q, "ё", "е");
		
		Set<String> matchedOptTokens = new HashSet<>();

		if(optRegexp != null) {
			Matcher matcher = optRegexp.matcher(q);
			while(matcher.find()) {
				 String group = matcher.group(0);
				 for(String t : StringUtils.split(group, tokenSeparators)) {
					 matchedOptTokens.add(t);
				 }
			}
		}
		
		String[] tokens = StringUtils.split(q, tokenSeparators);
		
		List<QToken> result = new ArrayList<QToken>(tokens.length);

		for(String t : tokens) {
			
			String withoutNumbers = StringUtils.replaceChars(t, "0123456789", "");
			
			boolean hasNumbers = withoutNumbers.length() != t.length();
			boolean numbersOnly = StringUtils.isBlank(withoutNumbers);
			boolean optional = optionals.contains(StringUtils.lowerCase(t)) 
					|| (!hasNumbers && withoutNumbers.length() < 3)
					|| matchedOptTokens.contains(t);
			
			result.add(new QToken(t, hasNumbers, numbersOnly, optional));
		}
		
		Query query = new Query(result);
		
		log.trace("Query: {}", query.print());
		
		return query;
	}

	protected String transform(String q) {
		return q;
	}
	
}
