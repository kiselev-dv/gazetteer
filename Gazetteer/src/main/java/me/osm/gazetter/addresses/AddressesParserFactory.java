package me.osm.gazetter.addresses;

import java.util.List;
import java.util.Set;

/**
 * Factory for AddressesParser, creates addresses parser with
 * injected dependencies, accordingly provided options, or with default
 * dependencies implementations. 
 * */
public interface AddressesParserFactory {
	
	/**
	 * Create AddressesParser instance
	 * 
	 * @param addressesSchemesParser
	 * 			Address scheme parser {@link AddressesSchemesParser}
	 * @param addrLevelComparator
	 * 			Sorter for addresses levels
	 * @param namesMatcherImpl
	 * 			Object which answers do objects equals by it's name/names
	 * 			For ex. match "Green st." "Green" "green st" and so on.
	 * @param cityBoundaryes
	 * 			Threat this addr parts names as city (location)
	 * @param addrTextFormatter
	 * 			Formatter for texts			
	 * @param sorting
	 * 			How to sort parts of address
	 * @param skippInFullText
	 * 			Skip in full address text
	 * @param findLangsLevel
	 * 			Search addresses translations or not
	 * 
	 * @return initialized parser 
	 * */
	public AddressesParser newAddressesParser(
			AddressesSchemesParser addressesSchemesParser,
			AddrLevelsComparator addrLevelComparator,
			NamesMatcher namesMatcherImpl, List<String> cityBoundaryes,
			AddrTextFormatter addrTextFormatter,
			AddrLevelsSorting sorting, Set<String> skippInFullText,
			boolean findLangsLevel);
	
}
