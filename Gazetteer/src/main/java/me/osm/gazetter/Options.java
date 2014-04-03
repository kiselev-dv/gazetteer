package me.osm.gazetter;

import java.io.File;

import groovy.lang.GroovyClassLoader;

import org.apache.commons.lang3.StringUtils;

import me.osm.gazetter.addresses.AddrLevelsSorting;
import me.osm.gazetter.addresses.AddressesParser;
import me.osm.gazetter.addresses.NamesMatcher;
import me.osm.gazetter.addresses.impl.AddressesParserImpl;
import me.osm.gazetter.addresses.impl.NamesMatcherImpl;
import me.osm.gazetter.join.PoiAddrJoinBuilder;

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

	public static void initialize(AddrLevelsSorting sorting, String groovyFormatter) {
		if(instance != null) {
			throw new SecondaryOptionsInitializationException();
		}
		
		AddressesParser adrParser = getAddrParser(groovyFormatter);
		
		instance = new Options(sorting, adrParser, new NamesMatcherImpl());
	}

	private static AddressesParser getAddrParser(String groovyFormatter) {
		AddressesParser adrParser = null;
		try {
			if(!StringUtils.isEmpty(groovyFormatter)) {
				GroovyClassLoader gcl = new GroovyClassLoader(Options.class.getClassLoader());
				Class clazz = gcl.parseClass(new File(groovyFormatter));
				Object aScript = clazz.newInstance();
				
				if(aScript instanceof AddressesParser) {
					adrParser = (AddressesParser) aScript;
				}
			}
			else {
				adrParser = new AddressesParserImpl();
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		return adrParser;
	}
	
	public static Options get() {
		if(instance == null) {
			instance = new Options();
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
