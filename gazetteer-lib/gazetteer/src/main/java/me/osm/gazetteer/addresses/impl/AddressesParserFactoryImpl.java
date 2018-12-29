package me.osm.gazetteer.addresses.impl;

import java.util.List;
import java.util.Set;

import me.osm.gazetteer.addresses.AddrLevelsComparator;
import me.osm.gazetteer.addresses.AddrLevelsSorting;
import me.osm.gazetteer.addresses.AddrTextFormatter;
import me.osm.gazetteer.addresses.AddressesParser;
import me.osm.gazetteer.addresses.AddressesParserFactory;
import me.osm.gazetteer.addresses.AddressesSchemesParser;
import me.osm.gazetteer.addresses.NamesMatcher;

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
