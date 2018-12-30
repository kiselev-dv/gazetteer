package me.osm.osmdoc.read.tagvalueparsers;

import java.util.HashMap;
import java.util.Map;

import me.osm.osmdoc.model.Tag;

public class TagValueParsersFactory {
	
	private static final EchoParser echoParser = new EchoParser();
	private static final DateParser dateParser = new DateParser();
	private static final NumberParser numberParser = new NumberParser();
	private static final OpeningHoursParser ohParser = new OpeningHoursParser();
	
	private static final Map<String, EnumParser> enumParsers = 
			new HashMap<String, EnumParser>();

	private static final Map<String, BooleanParser> boolParsers = 
			new HashMap<String, BooleanParser>();
	
	public static TagValueParser getParser(Tag tag) {
		
		switch(tag.getTagValueType()) {
		
		case ANY_STRING: return echoParser();
		
		case ENUM: return enumParser(tag);

		case BOOLEAN: return booleanParser(tag);
		
		case DATE: return dateParser;
		
		case NUMBER: return numberParser;
		
		case OPEN_HOURS: return ohParser;
		
		default: return echoParser();
		}
	}

	private static TagValueParser booleanParser(Tag tag) {
		String key = tag.getKey().getValue();
		BooleanParser parser = boolParsers.get(key);
		
		if(parser == null) {
			synchronized (boolParsers) {
				parser = boolParsers.get(key);
				if(parser == null) {
					parser = new BooleanParser(tag);
					boolParsers.put(key, parser);
				}
			}
		}
		
		return parser;
	}

	private static TagValueParser enumParser(Tag tag) {
		String key = tag.getKey().getValue();
		EnumParser enumParser = enumParsers.get(key);
		
		if(enumParser == null) {
			synchronized (enumParsers) {
				enumParser = enumParsers.get(key);
				if(enumParser == null) {
					enumParser = new EnumParser(tag, true);
					enumParsers.put(key, enumParser);
				}
			}
		}
		
		return enumParser;
	}

	private static TagValueParser echoParser() {
		return echoParser;
	}

}
