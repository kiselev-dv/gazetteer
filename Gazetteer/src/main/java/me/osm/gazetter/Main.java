package me.osm.gazetter;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import me.osm.gazetter.addresses.AddrLevelsSorting;
import me.osm.gazetter.join.Joiner;
import me.osm.gazetter.out.CSVOutWriter;
import me.osm.gazetter.split.Split;
import me.osm.gazetter.striper.Slicer;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import net.sourceforge.argparse4j.inf.Subparsers;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for executable jar.
 * */
public class Main {
	
	private static final Logger log = LoggerFactory.getLogger(Main.class);
	
	private static final String EXCCLUDE_POI_BRANCH_OPT = "--excclude-poi-branch";
	private static final String EXCCLUDE_POI_BRANCH_VAL = "excclude_poi_branch";
	
	private static final String ADDR_FORMATTER_OPT = "--addr-formatter";
	private static final String ADDR_FORMATTER_VAL = "addr_formatter";

	private static final String ADDR_ORDER_OPT = "--addr-order";
	private static final String ADDR_ORDER_VAL = "addr_order";
	
	private static final String JOIN_COMMON_VAL = "common";
	private static final String JOIN_COMMON_OPT = "--common";

	private static final String DATA_DIR_VAL = "data_dir";
	private static final String DATA_DIR_OPT = "--data-dir";
	
	private static final String LOG_OPT = "--log-level";
	private static final String LOG_VAL = "log_level";

	private static final String POI_CATALOG_VAL = "poi_catalog";
	private static final String POI_CATALOG_OPT = "--poi-catalog";

	private static final String FEATURE_TYPES_VAL = "feature_types";

	private static final String COMMAND = "command";

	public static interface CommandDescription {
		public String longName(); 
		public String help(); 
	}
	
	private enum Command implements CommandDescription {
	    SPLIT {
	    	public String longName() {return name().toLowerCase();}
	    	public String help() {return "Prepare osm data. Split nodes, ways and relations.";}
	    }, 
	    SLICE {
	    	public String longName() {return name().toLowerCase();}
	    	public String help() {return "Parse features from osm data and write it into stripes 0.1 degree wide.";}
	    }, 
	    JOIN {
	    	public String longName() {return name().toLowerCase();}
	    	public String help() {return "Join features. Made spatial joins for address points inside polygons and so on.";}
	    }, 
	    OUT_CSV {
	    	public String longName() {return name().toLowerCase().replace('_', '-');}
	    	public String help() {return "Write data out in csv format.";}
	    };

	};

	/**
	 * Parse arguments and run tasks accordingly.
	 * */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static void main(String[] args) {
		
		System.setProperty(org.slf4j.impl.SimpleLogger.SHOW_DATE_TIME_KEY, "true");
		System.setProperty(org.slf4j.impl.SimpleLogger.DATE_TIME_FORMAT_KEY, "yyyy-MM-dd HH.mm.ss.S");
		System.setProperty(org.slf4j.impl.SimpleLogger.SHOW_SHORT_LOG_NAME_KEY, "true");
		
		
		ArgumentParser parser = getArgumentsParser();
		
		try {
			Namespace namespace = parser.parseArgs(args);
			
			System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, (String)namespace.get(LOG_VAL));

			if(namespace.get(COMMAND).equals(Command.SPLIT)) {
				Split splitter = new Split(new File(namespace.getString(DATA_DIR_VAL)), namespace.getString("osm_file"));
				splitter.run();
			}

			if(namespace.get(COMMAND).equals(Command.SLICE)) {
				List<String> types = new ArrayList<String>();
				if(namespace.get(FEATURE_TYPES_VAL) instanceof String) {
					types.add((String)namespace.get(FEATURE_TYPES_VAL));
				}
				else if (namespace.get(FEATURE_TYPES_VAL) instanceof Collection) {
					types.addAll((Collection<String>)namespace.get(FEATURE_TYPES_VAL));
				}
				new Slicer(namespace.getString(DATA_DIR_VAL)).run(
						namespace.getString(POI_CATALOG_VAL), 
						types,
						(List)namespace.getList(EXCCLUDE_POI_BRANCH_VAL)
				);
				
			}

			if(namespace.get(COMMAND).equals(Command.JOIN)) {
				Options.initialize(
						AddrLevelsSorting.valueOf(namespace.getString(ADDR_ORDER_VAL)),
						namespace.getString(ADDR_FORMATTER_VAL)
				);
				
				new Joiner().run(namespace.getString(DATA_DIR_VAL), namespace.getString(JOIN_COMMON_VAL));
				
			}
			
			if(namespace.get(COMMAND).equals(Command.OUT_CSV)) {
				new CSVOutWriter(
						namespace.getString(DATA_DIR_VAL), 
						StringUtils.join(namespace.getList("columns"), ' '), 
						(List)namespace.getList("types"),
						namespace.getString("out_file")).write();
			}
			
		} 
		catch (ArgumentParserException e) {
			parser.handleError(e);
		}
		catch (Exception e) {
			log.error("Fatal error: " + ExceptionUtils.getRootCause(e).getMessage(), e);
			System.exit(1);
		} 
		
	}

	/**
	 * Generate arguments parser.
	 * */
	private static ArgumentParser getArgumentsParser() {
		ArgumentParser parser = ArgumentParsers.newArgumentParser("gazetter")
                .defaultHelp(true)
                .description("Create alphabetical index of osm file features");

        parser.addArgument(DATA_DIR_OPT).required(false).
                help("Use folder as data storage.").setDefault("slices");
        
        parser.addArgument(LOG_OPT).required(false).setDefault("WARN");
        
        Subparsers subparsers = parser.addSubparsers();
		
        //split
        {
        	Command command = Command.SPLIT;
			Subparser split = subparsers.addParser(command.longName())
        			.setDefault(COMMAND, command)
					.help(command.help());
        	
        	split.addArgument("osm_file").required(true)
        		.help("Path to osm file. *.osm *.osm.bz *.osm.gz supported.");
        }
        
		//slice
		{
			Command command = Command.SLICE;
			Subparser slice = subparsers.addParser(command.longName())
        			.setDefault(COMMAND, command)
					.help(command.help());
			
			slice.addArgument(POI_CATALOG_OPT).setDefault("jar")
				.help("Path to osm-doc catalog xml file. By default internal osm-doc.xml will be used.");
			
			slice.addArgument(EXCCLUDE_POI_BRANCH_OPT).nargs("*")
				.help("Exclude branch of osm-doc features hierarchy. "
					+ "Eg: osm-ru:transport where osm-ru is a name of the hierarchy, "
					+ "and transport is a name of the branch");
			
			slice.addArgument(FEATURE_TYPES_VAL).help("Parse and slice axact feature(s) type.")
				.choices(Slicer.sliceTypes).nargs("*").setDefault("all").setConst("all");
			
		}

		//join
		{
			Command command = Command.JOIN;
			Subparser join = subparsers.addParser(command.longName())
        			.setDefault(COMMAND, command)
					.help(command.help());
			
			join.addArgument(JOIN_COMMON_OPT)
				.help("Path for *.json with array of features which will be added to boundaries "
						+ "list for every feature.");
			
			join.addArgument(ADDR_ORDER_OPT).choices("HN_STREET_CITY", "STREET_HN_CITY", "CITY_STREET_HN").setDefault("HN_STREET_CITY")
				.help("How to sort addr levels in full addr text");

			join.addArgument(ADDR_FORMATTER_OPT)
				.help("Path to *.groovy file with full addresses texts formatter.");
			
		}

		//out
		{
			Command command = Command.OUT_CSV;
			Subparser outCSV = subparsers.addParser(command.longName())
        			.setDefault(COMMAND, command)
					.help(command.help());
			
			outCSV.addArgument("--columns").nargs("+");
			outCSV.addArgument("--types").nargs("+").choices(CSVOutWriter.ARG_TO_TYPE.keySet());
			outCSV.addArgument("--out-file").setDefault("-");
		}
		
		return parser;
	}

}
