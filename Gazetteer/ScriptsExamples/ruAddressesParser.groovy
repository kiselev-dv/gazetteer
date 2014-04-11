import java.util.List;
import java.util.Set;

import me.osm.gazetter.addresses.AddrLevelsComparator;
import me.osm.gazetter.addresses.AddrLevelsSorting;
import me.osm.gazetter.addresses.AddrTextFormatter;
import me.osm.gazetter.addresses.AddressesParser;
import me.osm.gazetter.addresses.AddressesParserFactory;
import me.osm.gazetter.addresses.AddressesSchemesParser;
import me.osm.gazetter.addresses.NamesMatcher;

import me.osm.gazetter.addresses.impl.AddressesParserImpl;
import me.osm.gazetter.addresses.impl.AddressesLevelsMatcherImpl;

import org.json.*;

class RuAddressesParserFactory implements AddressesParserFactory {

	public AddressesParser newAddressesParser(
			AddressesSchemesParser addressesSchemesParser,
			AddrLevelsComparator addrLevelComparator,
			NamesMatcher namesMatcherImpl, List<String> cityBoundaryes,
			AddrTextFormatter addrTextFormatter, AddrLevelsSorting sorting,
			Set<String> skippInFullText,
			boolean findLangsLevel) {
		
		
		return new AddressesParserImpl(
				addressesSchemesParser, 
				new AddressesLevelsMatcherImpl(addrLevelComparator, 
					namesMatcherImpl, 
					["place:hamlet", "place:village", "place:town", "place:city"]),
				addrTextFormatter, 
				sorting,
				skippInFullText,
				findLangsLevel);
	}
} 

