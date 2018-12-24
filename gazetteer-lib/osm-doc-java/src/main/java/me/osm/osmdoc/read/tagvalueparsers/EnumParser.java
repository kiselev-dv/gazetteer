package me.osm.osmdoc.read.tagvalueparsers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import me.osm.osmdoc.model.Tag;
import me.osm.osmdoc.model.Tag.Val;
import me.osm.osmdoc.model.Tag.Val.MatchType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnumParser implements TagValueParser {
	
	private static final Logger log = LoggerFactory.getLogger(EnumParser.class);

	private Map<String, Val> exacts = new HashMap<>();
	private Map<String, Val> contains = new HashMap<>();
	private Map<Pattern, Val> regexp = new HashMap<>();
	
	private String anyMatch = null;
	
	public EnumParser(Tag tag, boolean strict) {
		List<Val> values = tag.getVal();
		for(Val val : values) {
			String title = val.getTitle();
			MatchType match = val.getMatch();
			
			//TODO: multiple values
			if(MatchType.EXACT == match) {
				exacts.put(val.getValue().toLowerCase(), val);
			}
			else if(MatchType.CONTAINS == match || MatchType.WITH_NAMESPACE == match) {
				contains.put(val.getValue().toLowerCase(), val);
			}
			else if(MatchType.ANY == match) {
				anyMatch = title;
			}
			else if(MatchType.REGEXP == match) {
				try{
					regexp.put(Pattern.compile(val.getValue()), val);
				}
				catch (PatternSyntaxException e) {
					log.warn("Failed to compile regexp for tag {}. Regexp: {}.", tag.getKey().getValue(), val.getValue());
				}
			}
		}
	}
	
	@Override
	public Object parse(String rawValue) {
		
		String lowerCase = rawValue.toLowerCase();
		Val exact = exacts.get(lowerCase);
		if(exact != null) {
			return exact;
		}
		
		for(Entry<String, Val> ce : contains.entrySet()) {
			if(lowerCase.contains(ce.getKey())) {
				return ce.getValue();
			}
		}

		for(Entry<Pattern, Val> re : regexp.entrySet()) {
			if(re.getKey().matcher(lowerCase).find()) {
				return re.getValue();
			}
		}
		
		if(anyMatch != null && !lowerCase.equals("no") && lowerCase.equals("false")) {
			return anyMatch;
		}
		
		return null;
	}

}
