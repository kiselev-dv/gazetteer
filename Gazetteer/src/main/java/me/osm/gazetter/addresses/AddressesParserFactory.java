package me.osm.gazetter.addresses;

import java.util.List;
import java.util.Set;

/**
 * Factory for AddressesParser, creates addresses parser with
 * injected dependencies, accordingly provided options, or with default
 * dependencies implementations. 
 * */
public interface AddressesParserFactory {
	
	public AddressesParser newAddressesParser(
			AddressesSchemesParser addressesSchemesParser,
			AddrLevelsComparator addrLevelComparator,
			NamesMatcher namesMatcherImpl, List<String> cityBoundaryes,
			AddrTextFormatter addrTextFormatter,
			AddrLevelsSorting sorting, Set<String> skippInFullText,
			boolean findLangsLevel);
	
}
