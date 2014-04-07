package me.osm.gazetter;

import groovy.lang.GroovyClassLoader;

import java.io.File;
import java.util.Set;

import me.osm.gazetter.addresses.AddrLevelsComparator;
import me.osm.gazetter.addresses.AddrLevelsSorting;
import me.osm.gazetter.addresses.AddressesParser;
import me.osm.gazetter.addresses.NamesMatcher;
import me.osm.gazetter.addresses.impl.AddrTextFormatterImpl;
import me.osm.gazetter.addresses.impl.AddressesLevelsMatcherImpl;
import me.osm.gazetter.addresses.impl.AddressesParserImpl;
import me.osm.gazetter.addresses.impl.AddressesSchemesParserImpl;
import me.osm.gazetter.addresses.impl.NamesMatcherImpl;
import me.osm.gazetter.addresses.sorters.CityStreetHNComparator;
import me.osm.gazetter.addresses.sorters.HNStreetCityComparator;
import me.osm.gazetter.addresses.sorters.StreetHNCityComparator;

import org.apache.commons.lang3.StringUtils;

public class Options {
	
	public static final class SecondaryOptionsInitializationException
		extends RuntimeException {

		private static final long serialVersionUID = -2739610432163207207L;
		
	}
	
	private static volatile Options instance;
	private final AddrLevelsSorting sorting;
	private final AddressesParser addressesParser;
	private final NamesMatcher namesMatcher;
	

	private Options() {
		sorting = AddrLevelsSorting.HN_STREET_CITY;
		addressesParser = new AddressesParserImpl();
		namesMatcher = new NamesMatcherImpl();
	}

	private Options(AddrLevelsSorting sorting, AddressesParser addressesParser, NamesMatcher namesMatcher) {
		this.sorting = sorting;
		this.addressesParser = addressesParser;
		this.namesMatcher = namesMatcher;
	}

	public static void initialize(AddrLevelsSorting sorting, String groovyFormatter, Set<String> skippInFullText) {
		if(instance != null) {
			throw new SecondaryOptionsInitializationException();
		}
		
		AddressesParser adrParser = getAddrParser(groovyFormatter, sorting, skippInFullText);
		
		instance = new Options(sorting, adrParser, new NamesMatcherImpl());
	}

	private static AddressesParser getAddrParser(String groovyFormatter, AddrLevelsSorting sorting, Set<String> skippInFullText) {
		
		AddressesParser adrParser = null;
		
		try {
			if(!StringUtils.isEmpty(groovyFormatter)) {
				GroovyClassLoader gcl = new GroovyClassLoader(Options.class.getClassLoader());
				try
				{
					Class<?> clazz = gcl.parseClass(new File(groovyFormatter));
					Object aScript = clazz.newInstance();
					
					if(aScript instanceof AddressesParser) {
						adrParser = (AddressesParser) aScript;
					}
				}
				finally {
					gcl.close();
				}
			}
			else {
				AddrLevelsComparator addrLevelComparator;
				if(AddrLevelsSorting.HN_STREET_CITY == sorting) {
					addrLevelComparator = new HNStreetCityComparator();
				}
				else if (AddrLevelsSorting.CITY_STREET_HN == sorting) {
					addrLevelComparator = new CityStreetHNComparator();
				}
				else {
					addrLevelComparator = new StreetHNCityComparator();
				}
				
				adrParser = new AddressesParserImpl(
						new AddressesSchemesParserImpl(), 
						new AddressesLevelsMatcherImpl(addrLevelComparator, new NamesMatcherImpl()), 
						new AddrTextFormatterImpl(),
						sorting,
						skippInFullText);
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		return adrParser;
	}
	
	public static Options get() {
		if(instance == null) {
			synchronized(instance) {
				if(instance == null) {
					instance = new Options();
				}
			}
		}
		return instance;
	}

	public AddrLevelsSorting getSorting() {
		return sorting;
	}

	public AddressesParser getAddressesParser() {
		return addressesParser;
	}

	public NamesMatcher getNamesMatcher() {
		return namesMatcher;
	}
	
}
