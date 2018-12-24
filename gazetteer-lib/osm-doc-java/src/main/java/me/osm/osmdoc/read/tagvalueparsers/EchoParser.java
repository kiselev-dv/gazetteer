package me.osm.osmdoc.read.tagvalueparsers;

public class EchoParser implements TagValueParser {

	@Override
	public Object parse(String rawValue) {
		return rawValue;
	}

}
