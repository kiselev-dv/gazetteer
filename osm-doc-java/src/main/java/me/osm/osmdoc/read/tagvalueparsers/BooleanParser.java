package me.osm.osmdoc.read.tagvalueparsers;

import java.util.List;

import me.osm.osmdoc.model.Tag;
import me.osm.osmdoc.model.Tag.Val;


public class BooleanParser implements TagValueParser {
	
	private Boolean def = null;
	
	public BooleanParser(Tag tag) {
		List<Val> val = tag.getVal();
		for (Val v : val) {
			if(v.isDefault() != null) {
				def = Boolean.valueOf(v.getValue());
			}
		}
	}

	@Override
	public Object parse(String rawValue) {
		
		String lowerCase = rawValue.toLowerCase();
		
		if("yes".equals(lowerCase) || "true".equals(lowerCase)) {
			return Boolean.TRUE;
		}
		else if("no".equals(lowerCase) || "false".equals(lowerCase)) {
			return Boolean.FALSE;
		}
		
		if("unknown".equals(lowerCase)) {
			return null;
		}
		
		return def;
	}

}
