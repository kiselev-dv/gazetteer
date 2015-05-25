package me.osm.gazetter;

import groovy.lang.GroovyClassLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import me.osm.gazetter.join.out_handlers.GazetteerOutWriter;
import me.osm.gazetter.join.out_handlers.JoinOutHandler;
import me.osm.gazetter.join.out_handlers.PrintJoinOutHandler;
import me.osm.gazetter.out.CSVOutWriter;

import org.apache.commons.lang3.StringUtils;

/**
 * Stores command line provided options.
 * */
public class Options {
	
	public static final class SecondaryOptionsInitializationException
		extends RuntimeException {

		private static final long serialVersionUID = -2739610432163207207L;
		
	}
	
	private static final Map<String, JoinOutHandler> predefinedJoinOutHandlers = new HashMap<String, JoinOutHandler>();
	static {
		predefinedJoinOutHandlers.put(PrintJoinOutHandler.NAME, new PrintJoinOutHandler());
		predefinedJoinOutHandlers.put(GazetteerOutWriter.NAME, new GazetteerOutWriter());
		predefinedJoinOutHandlers.put(CSVOutWriter.NAME, new CSVOutWriter());
	}
	
	public static Collection<String> getPredefinedOutHandlers() {
		return predefinedJoinOutHandlers.keySet();
	}
	
	private static volatile Options instance;
	private final AddrLevelsSorting sorting;
	private final AddressesParser addressesParser;
	private final NamesMatcher namesMatcher;
	private boolean findLangsLevel;
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
			if (handlers != null && !handlers.isEmpty()) {
				
				List<List<String>> groups = new ArrayList<List<String>>();
				
				for(String sh : handlers) {
					if(sh.endsWith(".groovy") || predefinedJoinOutHandlers.keySet().contains(sh)) {
						groups.add(new ArrayList<String>());
					}
					
					if(groups.isEmpty()) {
						return;
					}
					
					groups.get(groups.size() - 1).add(sh);
				}
				
				for(List<String> handlerDef : groups) {
					String handlerPath = handlerDef.remove(0);
					if(handlerPath.endsWith(".groovy")) {
						GroovyClassLoader gcl = new GroovyClassLoader(Options.class.getClassLoader());
						try
						{
							Class<?> clazz = gcl.parseClass(new File(handlerPath));
							Object aScript = clazz.newInstance();
							
							if (aScript instanceof JoinOutHandler) {
								JoinOutHandler joinOutHandler = ((JoinOutHandler)aScript).newInstance(handlerDef);
								joinHandlers.add(joinOutHandler);
							}
						}
						finally {
							gcl.close();
						}
					}
					else if (predefinedJoinOutHandlers.containsKey(handlerPath)){
						joinHandlers.add(predefinedJoinOutHandlers.get(handlerPath).newInstance(handlerDef));
					}
				}
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		
		if(joinHandlers.isEmpty()) {
			joinHandlers.add(predefinedJoinOutHandlers.get(
					GazetteerOutWriter.NAME).newInstance(new ArrayList<String>()));
		}
	}
	
	public Collection<JoinOutHandler> getJoinOutHandlers() {
		return joinHandlers;
	}
}
