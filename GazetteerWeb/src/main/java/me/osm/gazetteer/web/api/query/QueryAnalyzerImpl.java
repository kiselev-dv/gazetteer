package me.osm.gazetteer.web.api.query;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.osm.gazetteer.web.Main;
import me.osm.gazetteer.web.imp.IndexHolder;
import me.osm.gazetteer.web.imp.Replacer;
import me.osm.gazetteer.web.utils.ReplacersCompiler;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueryAnalyzerImpl implements QueryAnalyzer {
	
	private static final Logger log = LoggerFactory.getLogger(QueryAnalyzerImpl.class);

	private static final String tokenSeparators = Main.config().getQueryAnalyzerSeparators();
	private static final String removeChars = Main.config().getRemoveCharacters();
	
	private static final Pattern groupPattern = Pattern.compile("GROUP[0-9]+");
	
	private static final List<String[]> charReplaces = IndexHolder.getCharFilterReplaces();
	
	public static final Set<String> optionals = new HashSet<String>(); 
	public static Pattern optRegexp = null;
	static {
		readOptionals();
	}
	
	public static final List<Replacer> searchReplacers = new ArrayList<>();
	static {
		ReplacersCompiler.compile(searchReplacers, new File("config/replacers/requiredSearchReplacers"));
	}
	
	@SuppressWarnings("unchecked")
	private static void readOptionals() {
		try {
			Set<String> patterns = new HashSet<>();
			for(String option : (List<String>)IOUtils.readLines(QueryAnalyzerImpl.class.getResourceAsStream("/optional"))) {
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
	
	/* (non-Javadoc)
	 * @see me.osm.gazetteer.web.api.imp.QueryAnalyzer#getQuery(java.lang.String)
	 */
	@Override
	public Query getQuery(String q) {
		
		if(null == q) {
			return null;
		}
		
		String original = q;
		
		q = StringUtils.replaceChars(q, removeChars, null);

		q = q.toLowerCase();
		
		// See: gazetteer_schema.json settings.analysis.char_filter.*.mappings
		for(String[] r : charReplaces) {
			q = StringUtils.replace(q, r[0], r[1]);
		}

		LinkedHashMap<String, Collection<String>> groups = new LinkedHashMap<>();
		for(Replacer r : searchReplacers) {
			groups.putAll(r.replaceGroups(q));
		}
		
		HashMap<String, String> groupAliases = new HashMap<>();
		
		int i = 0;
		for(Entry<String, Collection<String>> gk : groups.entrySet()) {
			String alias = "GROUP" + i++;
			groupAliases.put(alias, gk.getKey());

			q = StringUtils.replace(q, gk.getKey(), alias);
		}
		
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

			List<String> variants = new ArrayList<>();
			if(StringUtils.startsWith(t, "GROUP")) {
				Matcher matcher = groupPattern.matcher(t);
				if(matcher.find()) {
					String matched = matcher.group();
					String groupKey = groupAliases.get(matched);
					if(groupKey != null) {
						String tail = StringUtils.remove(t, matched);
						t = groupKey + tail;
						variants = new ArrayList<>();
						for(String var : groups.get(groupKey)) {
							variants.add(var + tail);
						}
					}
				}
			}
			
			String withoutNumbers = StringUtils.replaceChars(t, "0123456789", "");
			
			boolean hasNumbers = withoutNumbers.length() != t.length();
			boolean numbersOnly = StringUtils.isBlank(withoutNumbers);
			boolean optional = optionals.contains(StringUtils.lowerCase(t)) 
					|| (!hasNumbers && withoutNumbers.length() < 3)
					|| matchedOptTokens.contains(t);
			
			result.add(new QToken(t, variants, hasNumbers, numbersOnly, optional));
		}
		
		Query query = new Query(result, original, null);
		
		log.trace("Query: {}", query.print());
		
		return query;
	}
	
}
