package me.osm.gazetter;

import java.io.File;

import groovy.lang.GroovyClassLoader;

import org.apache.commons.lang3.StringUtils;

import me.osm.gazetter.addresses.AddrLevelsSorting;
import me.osm.gazetter.addresses.AddressesParser;
import me.osm.gazetter.addresses.impl.AddressesParserImpl;

public class Options {
	
	public static final class SecondaryOptionsInitializationException
		extends RuntimeException {

		private static final long serialVersionUID = -2739610432163207207L;
		
	}
	
	private static volatile Options instance;
	private AddrLevelsSorting sorting;
	private AddressesParser addressesParser;

	private Options() {
		
	}

	public static void initialize(AddrLevelsSorting sorting, String groovyFormatter) {
		if(instance != null) {
			throw new SecondaryOptionsInitializationException();
		}
		
		instance = new Options();
		instance.sorting = sorting;
		
		try {
			if(!StringUtils.isEmpty(groovyFormatter)) {
				GroovyClassLoader gcl = new GroovyClassLoader(Options.class.getClassLoader());
				Class clazz = gcl.parseClass(new File(groovyFormatter));
				Object aScript = clazz.newInstance();
				
				if(aScript instanceof AddressesParser) {
					instance.addressesParser = (AddressesParser) aScript;
				}
			}
			else {
				 instance.addressesParser = new AddressesParserImpl();
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public static Options get() {
		return instance;
	}

	public AddrLevelsSorting getSorting() {
		return sorting;
	}

	public AddressesParser getAddressesParser() {
		return addressesParser;
	}
	
}
