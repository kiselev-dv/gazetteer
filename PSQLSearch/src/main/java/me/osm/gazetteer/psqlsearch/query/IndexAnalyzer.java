package me.osm.gazetteer.psqlsearch.query;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;

import org.apache.commons.lang3.StringUtils;

import gcardone.junidecode.Junidecode;
import me.osm.gazetteer.psqlsearch.query.IndexAnalyzer.Token;

public class IndexAnalyzer {
	
	private static final int MINIMAL_MEANING_TERM_LENGTH = 3;
	private List<Replacer> hnReplacers = new ArrayList<>(); 
	private List<Replacer> streetsReplacers = new ArrayList<>();
	private List<Replacer> localityReplacers = new ArrayList<>();
	
	public IndexAnalyzer() {
		ReplacersCompiler.compile(hnReplacers, new File("config/replacers/index/hnIndexReplasers"));
		ReplacersCompiler.compile(streetsReplacers, new File("config/replacers/index/streetsReplacers"));
		ReplacersCompiler.compile(localityReplacers, new File("config/replacers/index/localityReplacers"));
	} 
	
	public String toASCII(String original) {
		return Junidecode.unidecode(original);
	}
	
	public static final class Token {
		public String token;
		public boolean optional;
		
		public Token(String token, boolean optional) {
			this.token = token;
			this.optional = optional;
		}
	}
	
	public List<Token> normalizeLocationName(String original, boolean doASCII) {
		Set<String> uniqueTokens = listUniqueTokens(original, localityReplacers);
		
		Set<String> matchedOptTokens = findOptionals(uniqueTokens);
		
		return asTokens(doASCII, uniqueTokens, matchedOptTokens);
	}
	
	public List<Token> normalizeStreetName(String original, boolean doASCII) {
		
		Set<String> uniqueTokens = listUniqueTokens(original, streetsReplacers);
		
		Set<String> matchedOptTokens = findOptionals(uniqueTokens);
		
		return asTokens(doASCII, uniqueTokens, matchedOptTokens);
	}
	
	public List<Token> normalizeName(String original, boolean doASCII) {
		Set<String> uniqueTokens = listUniqueTokens(original, streetsReplacers);
		
		Set<String> matchedOptTokens = findOptionals(uniqueTokens);
		
		return asTokens(doASCII, uniqueTokens, matchedOptTokens);
	}

	private List<Token> asTokens(boolean doASCII, Set<String> uniqueTokens, Set<String> matchedOptTokens) {
		List<Token> result = new ArrayList<>();

		for (String token : uniqueTokens) {
			// in case replacer returned upper case
			token = token.toLowerCase();
			
			boolean optional = QueryAnalyzerImpl.optionals.contains(token) || matchedOptTokens.contains(token);
			if (token.length() <= MINIMAL_MEANING_TERM_LENGTH) {
				optional = true;
			}
			if (StringUtils.containsAny(token, "0123456789")) {
				optional = false;
			}
			
			if (StringUtils.isNoneBlank(token)) {
				result.add(new Token(token, optional));
				if (doASCII) {
					String ascii = toASCII(token);
					ascii = StringUtils.replaceChars(ascii, QueryAnalyzerImpl.removeChars, null);
					result.add(new Token(ascii, optional));
				}
			}
		}
		
		return result;
	}

	private Set<String> findOptionals(Set<String> uniqueTokens) {
		Set<String> matchedOptTokens = new HashSet<>();
		if(QueryAnalyzerImpl.optRegexp != null) {
			Matcher matcher = QueryAnalyzerImpl.optRegexp.matcher(StringUtils.join(uniqueTokens, ' '));
			while(matcher.find()) {
				 String group = matcher.group(0);
				 for(String t : StringUtils.split(group, QueryAnalyzerImpl.tokenSeparators)) {
					 matchedOptTokens.add(t);
				 }
			}
		}
		
		return matchedOptTokens;
	}
	
	private Set<String> listUniqueTokens(String original, List<Replacer> replacers) {
		original = StringUtils.stripToEmpty(original);
		String s = StringUtils.join(transform(original.toLowerCase(), replacers), ' ');
		s = StringUtils.join(original, s);
		s = StringUtils.replaceChars(s, QueryAnalyzerImpl.removeChars, null);
		String[] tokens = StringUtils.split(s, QueryAnalyzerImpl.tokenSeparators);
		return new LinkedHashSet<>(Arrays.asList(tokens));
	}

	public Collection<String> getHNVariants(String original) {
		Collection<String> variants = transform(original, hnReplacers);
		if (variants.isEmpty()) {
			variants.add(original);
		}
		return variants;
	}
	
	private Collection<String> transform(String optString, Collection<Replacer> replacers) {
		
		for(String [] replacer : QueryAnalyzerImpl.charReplaces) {
			optString = StringUtils.replace(optString, replacer[0], replacer[1]);
		}
		
		Set<String> result = new HashSet<>(); 
		for(Replacer replacer : replacers) {
			try {
				Collection<String> replace = replacer.replace(optString);
				if(replace != null) {
					for(String s : replace) {
						if(StringUtils.isNotBlank(s) && !"null".equals(s)) {
							result.add(s);
						}
					}
				}
			}
			catch (Exception e) {
				
			}
		}
		
		return result;
	}

}
