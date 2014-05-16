package me.osm.gazetter.addresses.impl;

import java.util.List;
import java.util.Set;

import me.osm.gazetter.addresses.AddrLevelsComparator;
import me.osm.gazetter.addresses.AddrLevelsSorting;
import me.osm.gazetter.addresses.AddrTextFormatter;
import me.osm.gazetter.addresses.AddressesParser;
import me.osm.gazetter.addresses.AddressesParserFactory;
import me.osm.gazetter.addresses.AddressesSchemesParser;
import me.osm.gazetter.addresses.NamesMatcher;

/**
 * Default implementation for
 * {@link AddressesParserFactory}
 * */
public class AddressesParserFactoryImpl implements AddressesParserFactory {

	@Override
	public AddressesParser newAddressesParser(
			AddressesSchemesParser addressesSchemesParser,
			AddrLevelsComparator addrLevelComparator,
			NamesMatcher namesMatcherImpl, List<String> cityBoundaryes,
			AddrTextFormatter addrTextFormatter, AddrLevelsSorting sorting,
			Set<String> skippInFullText,
			boolean findLangsLevel) {
		
		
		return new AddressesParserImpl(
				addressesSchemesParser, 
				new AddressesLevelsMatcherImpl(addrLevelComparator, namesMatcherImpl, cityBoundaryes),
				addrTextFormatter, 
				sorting,
				skippInFullText,
				findLangsLevel);
	}


}
