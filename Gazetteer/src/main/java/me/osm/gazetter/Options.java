package me.osm.gazetter;

import groovy.lang.GroovyClassLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import me.osm.gazetter.addresses.AddrLevelsComparator;
import me.osm.gazetter.addresses.AddrLevelsSorting;
import me.osm.gazetter.addresses.AddressesParser;
import me.osm.gazetter.addresses.AddressesParserFactory;
import me.osm.gazetter.addresses.NamesMatcher;
import me.osm.gazetter.addresses.impl.AddrTextFormatterImpl;
import me.osm.gazetter.addresses.impl.AddressesParserFactoryImpl;
import me.osm.gazetter.addresses.impl.AddressesParserImpl;
import me.osm.gazetter.addresses.impl.AddressesSchemesParserImpl;
import me.osm.gazetter.addresses.impl.NamesMatcherImpl;
import me.osm.gazetter.addresses.sorters.CityStreetHNComparator;
import me.osm.gazetter.addresses.sorters.HNStreetCityComparator;
import me.osm.gazetter.addresses.sorters.StreetHNCityComparator;
import me.osm.gazetter.join.out_handlers.JoinOutHandler;
import me.osm.gazetter.out.CSVOutLineHandler;

import org.apache.commons.lang3.StringUtils;

/**
 * Stores command line provided options.
 * */
public class Options {
	
	public static final class SecondaryOptionsInitializationException
		extends RuntimeException {

		private static final long serialVersionUID = -2739610432163207207L;
		
	}
	
	private static volatile Options instance;
	private final AddrLevelsSorting sorting;
	private final AddressesParser addressesParser;
	private final NamesMatcher namesMatcher;
	private boolean findLangsLevel;
	private CSVOutLineHandler csvOutLineHandler = null;
	private int nThreads = Runtime.getRuntime().availableProcessors();
	private boolean compress = true;
	private List<JoinOutHandler> joinHandlers = new ArrayList<>();
	

	private Options() {
		sorting = AddrLevelsSorting.HN_STREET_CITY;
		addressesParser = new AddressesParserImpl();
		namesMatcher = new NamesMatcherImpl();
		this.findLangsLevel = false;
	}

	private Options(AddrLevelsSorting sorting, AddressesParser addressesParser, 
			NamesMatcher namesMatcher, boolean findLangs) {
		
		this.sorting = sorting;
		this.addressesParser = addressesParser;
		this.namesMatcher = namesMatcher;
		this.findLangsLevel = findLangs;
	}

	public static void initialize(AddrLevelsSorting sorting, String groovyFormatter, 
			Set<String> skippInFullText, boolean findLangs) {
		
		if(instance != null) {
			throw new SecondaryOptionsInitializationException();
		}
		
		AddressesParser adrParser = getAddrParser(groovyFormatter, sorting, skippInFullText, findLangs);
		
		instance = new Options(sorting, adrParser, new NamesMatcherImpl(), findLangs);
	}

	private static CSVOutLineHandler getCSVHandler(String csvOutLineHandlerFile) {
		
		try {
			
			if(StringUtils.isNotEmpty(csvOutLineHandlerFile)) {
				GroovyClassLoader gcl = new GroovyClassLoader(Options.class.getClassLoader());
				try
				{
					Class<?> clazz = gcl.parseClass(new File(csvOutLineHandlerFile));
					Object aScript = clazz.newInstance();
					
					if(aScript instanceof CSVOutLineHandler) {
						return (CSVOutLineHandler) aScript;
					}
				}
				finally {
					gcl.close();
				}
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		
		return null;
	}

	private static AddressesParser getAddrParser(String groovyFormatter, AddrLevelsSorting sorting, 
			Set<String> skippInFullText, boolean findLangs) {
		
		AddressesParser adrParser = null;
		
		try {
			
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

			if(!StringUtils.isEmpty(groovyFormatter)) {
				GroovyClassLoader gcl = new GroovyClassLoader(Options.class.getClassLoader());
				try
				{
					Class<?> clazz = gcl.parseClass(new File(groovyFormatter));
					Object aScript = clazz.newInstance();
					
					if(aScript instanceof AddressesParserFactory) {
						
						AddressesParserFactory factory = (AddressesParserFactory) aScript;
						
						adrParser = factory.newAddressesParser(
								new AddressesSchemesParserImpl(),
								addrLevelComparator, 
								new NamesMatcherImpl(),
								Arrays.asList("place:hamlet", "place:village", "place:town", "place:city", "boundary:8"), 
								new AddrTextFormatterImpl(), 
								sorting, 
								skippInFullText,
								findLangs);
					}
					else if(aScript instanceof AddressesParser) {
						adrParser = (AddressesParser) aScript;
					}
				}
				finally {
					gcl.close();
				}
			}
			else {
			
				AddressesParserFactory defFactory = new AddressesParserFactoryImpl();
				
				adrParser = defFactory.newAddressesParser(
						new AddressesSchemesParserImpl(),
						addrLevelComparator, 
						new NamesMatcherImpl(),
						Arrays.asList("place:hamlet", "place:village", "place:town", "place:city", "boundary:8"), 
						new AddrTextFormatterImpl(), 
						sorting, 
						skippInFullText,
						findLangs);
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		return adrParser;
	}
	
	public static Options get() {
		if(instance == null) {
			synchronized (Options.class) {
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

	public boolean isFindLangs() {
		return findLangsLevel;
	}

	public CSVOutLineHandler getCsvOutLineHandler() {
		return csvOutLineHandler;
	}

	public void setCsvOutLineHandler(String csvOutLineHandler) {
		this.csvOutLineHandler = getCSVHandler(csvOutLineHandler);
	}

	public int getNumberOfThreads () {
		return this.nThreads;
	}

	public void setNThreads(int nThreads) {
		this.nThreads = nThreads;
	}

	public void setCompress(boolean compress) {
		this.compress = compress;
	}

	public boolean isCompress() {
		return compress;
	}

	public void setJoinHandlers(List<String> handlers) {
		try {
			if (handlers == null || handlers.isEmpty()) {
				
				List<List<String>> groups = new ArrayList<List<String>>();
				
				for(String sh : handlers) {
					if(sh.endsWith(".groovy")) {
						groups.add(new ArrayList<String>());
					}
					
					groups.get(groups.size() - 1).add(sh);
				}
				
				GroovyClassLoader gcl = new GroovyClassLoader(Options.class.getClassLoader());
				try
				{
					for(List<String> handlerDef : groups) {
						String handlerPath = handlerDef.remove(0);
						Class<?> clazz = gcl.parseClass(new File(handlerPath));
						Object aScript = clazz.newInstance();
						
						
						if (aScript instanceof JoinOutHandler) {
							JoinOutHandler joinOutHandler = ((JoinOutHandler)aScript).newInstacne(handlerDef);
							joinHandlers.add(joinOutHandler);
						}
					}
					
				}
				finally {
					gcl.close();
				}
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public Collection<JoinOutHandler> getJoinOutHandlers() {
		return joinHandlers;
	}
	
}
