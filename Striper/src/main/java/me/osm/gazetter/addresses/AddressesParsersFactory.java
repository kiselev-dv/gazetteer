package me.osm.gazetter.addresses;

import java.util.ArrayList;
import java.util.List;

import me.osm.gazetter.addresses.parsers.RegularParser;

public class AddressesParsersFactory {
	
	private AddrElementsOrder addrElementsOrder = AddrElementsOrder.SMALL_TO_BIG;
	
	private volatile List<AddressesParser> parsers = null;

	public AddressesParsersFactory(AddrElementsOrder addrElementsOrder) {
		this.addrElementsOrder = addrElementsOrder;
	}
	
	public List<AddressesParser> getParsers() {
		
		if(parsers == null) {
			synchronized (this) {
				if(parsers == null) {
					parsers = createParsers();
				}
			}
		}
		
		return parsers;
	}

	private List<AddressesParser> createParsers() {
		List<AddressesParser> list = new ArrayList<>();
		
		list.add(new RegularParser(addrElementsOrder));
		
		return list;
	}
}
