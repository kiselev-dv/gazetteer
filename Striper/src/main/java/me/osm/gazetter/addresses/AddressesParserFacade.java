package me.osm.gazetter.addresses;

import java.util.Collections;
import java.util.List;

import org.json.JSONObject;

public class AddressesParserFacade {
	
	private List<AddressesParser> parsers;

	public AddressesParserFacade(AddressesParsersFactory parserFactory){
		this.parsers = parserFactory.getParsers();
	}
	
	public List<Address> parse(JSONObject addrPoint, List<JSONObject> boundaries) {
		
		for(AddressesParser parser : parsers) {
			List<Address> parse = parser.parse(addrPoint, boundaries);
			if(parse != null && !parse.isEmpty()) {
				return parse;
			}
		}
		
		return Collections.emptyList();
	}
	
}
