package me.osm.osmdoc.read.tagvalueparsers;

import org.apache.commons.lang3.StringUtils;

public class NumberParser implements TagValueParser {

	@Override
	public Object parse(String rawValue) {
		try {
			if(StringUtils.contains(rawValue, '.')) {
				return Double.valueOf(rawValue);
			}
			return Integer.valueOf(rawValue);
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

}
