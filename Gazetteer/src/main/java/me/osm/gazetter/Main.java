package me.osm.gazetter;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import me.osm.gazetter.addresses.AddrLevelsSorting;
import me.osm.gazetter.join.Joiner;
import me.osm.gazetter.out.CSVOutWriter;
import me.osm.gazetter.sortupdate.SortUpdate;
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
	
	private static final String NAMED_POI_BRANCH_OPT = "--named-poi-branch";
	private static final String NAMED_POI_BRANCH_VAL = "named_poi_branch";

	private static final String EXCCLUDE_POI_BRANCH_OPT = "--excclude-poi-branch";
	private static final String EXCCLUDE_POI_BRANCH_VAL = "excclude_poi_branch";
	
	private static final String ADDR_FORMATTER_OPT = "--addr-parser";
	private static final String ADDR_FORMATTER_VAL = "addr_parser";

	private static final String ADDR_ORDER_OPT = "--addr-order";
	private static final String ADDR_ORDER_VAL = "addr_order";
	
	private static final String JOIN_COMMON_VAL = "common";
	private static final String JOIN_COMMON_OPT = "--common";

	private static final String DATA_DIR_VAL = "data_dir";
	private static final String DATA_DIR_OPT = "--data-dir";
	
	private static final String LOG_OPT = "--log-level";
	private static final String LOG_FILE_OPT = "--log-file";

	private static final String POI_CATALOG_VAL = "poi_catalog";
	private static final String POI_CATALOG_OPT = "--poi-catalog";

	private static final String FEATURE_TYPES_VAL = "feature_types";

	private static final String COMMAND = "command";
	
	private static Logger log;

	public static interface CommandDescription {
		public String longName(); 
		public String help(); 
	}
	
	private enum Command implements CommandDescription {
	    SPLIT {
	    	@Override
			public String longName() {return name().toLowerCase();}
	    	@Override
			public String help() {return "Prepare osm data. Split nodes, ways and relations.";}
	    }, 
	    SLICE {
	    	@Override
			public String longName() {return name().toLowerCase();}
	    	@Override
			public String help() {return "Parse features from osm data and write it into stripes 0.1 degree wide.";}
	    }, 
	    JOIN {
	    	@Override
			public String longName() {return name().toLowerCase();}
	    	@Override
			public String help() {return "Join features. Made spatial joins for address points inside polygons and so on.";}
	    }, 
	    SYNCHRONIZE {
	    	@Override
			public String longName() {return name().toLowerCase();}
	    	@Override
			public String help() {return "Sort and update features. Remove outdated dublicates.";}
	    }, 
	    OUT_CSV {
	    	@Override
			public String longName() {return name().toLowerCase().replace('_', '-');}
	    	@Override
			public String help() {return "Write data out in csv format.";}
	    };

	};

	/**
	 * Parse arguments and run tasks accordingly.
	 * */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static void main(String[] args) {
		
		initLog(args);
		log = LoggerFactory.getLogger(Main.class);
		
		ArgumentParser parser = getArgumentsParser();
		
		try {
			Namespace namespace = parser.parseArgs(args);
			

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
						list(namespace.getList(EXCCLUDE_POI_BRANCH_VAL)),
						list(namespace.getList(NAMED_POI_BRANCH_VAL)),
						list(namespace.getList("drop"))
				);
				
			}

			if(namespace.get(COMMAND).equals(Command.JOIN)) {
				Options.initialize(
						AddrLevelsSorting.valueOf(namespace.getString(ADDR_ORDER_VAL)),
						namespace.getString(ADDR_FORMATTER_VAL),
						new HashSet(list(namespace.getList("skip_in_text"))),
						namespace.getBoolean("find_langs")
				);
				
				new Joiner(new HashSet(list(namespace.getList("check_boundaries"))))
					.run(namespace.getString(DATA_DIR_VAL), namespace.getString(JOIN_COMMON_VAL));
				new SortUpdate(namespace.getString(DATA_DIR_VAL)).run();
			}

			if(namespace.get(COMMAND).equals(Command.SYNCHRONIZE)) {
				new SortUpdate(namespace.getString(DATA_DIR_VAL)).run();
			}
			
			if(namespace.get(COMMAND).equals(Command.OUT_CSV)) {

				Options.get().setCsvOutLineHandler(namespace.getString("line_handler"));
				new CSVOutWriter(
						namespace.getString(DATA_DIR_VAL), 
						StringUtils.join(list(namespace.getList("columns")), ' '), 
						list(namespace.getList("types")),
						namespace.getString("out_file"),
						namespace.getString(POI_CATALOG_VAL)).write();
			}
			
		} 
		catch (ArgumentParserException e) {
			parser.handleError(e);
		}
		catch (Exception e) {
			Throwable rootCause = ExceptionUtils.getRootCause(e);
			log.error("Fatal error: " + (rootCause == null ? "" : rootCause.getMessage()), e);
			System.exit(1);
		} 
		
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static List<String> list( List list) {
		if(list == null) {
			return Collections.emptyList();
		}
		return list;
	}

	private static void initLog(String[] args) {
		System.setProperty(org.slf4j.impl.SimpleLogger.SHOW_DATE_TIME_KEY, "true");
		System.setProperty(org.slf4j.impl.SimpleLogger.DATE_TIME_FORMAT_KEY, "yyyy-MM-dd HH.mm.ss.S");
		System.setProperty(org.slf4j.impl.SimpleLogger.SHOW_SHORT_LOG_NAME_KEY, "true");

		Iterator<String> iterator = Arrays.asList(args).iterator();
		while(iterator.hasNext()) {
			if(iterator.next().equals(LOG_OPT) && iterator.hasNext()) {
				System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, iterator.next());
			}
			
			if(iterator.next().equals(LOG_FILE_OPT) && iterator.hasNext()) {
				System.setProperty(org.slf4j.impl.SimpleLogger.LOG_FILE_KEY, iterator.next());
			}
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
        parser.addArgument(LOG_FILE_OPT).required(false);
        
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

			slice.addArgument(NAMED_POI_BRANCH_OPT).nargs("*")
				.help("Kepp POIS from this banch only if they have name tag");
			
			slice.addArgument(FEATURE_TYPES_VAL).help("Parse and slice axact feature(s) type.")
				.choices(Slicer.sliceTypes).nargs("*").setDefault("all").setConst("all");

			slice.addArgument("--drop").nargs("*")
				.help("List of objects osm ids which will be dropped ex r60189.");
			
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

			join.addArgument("--check-boundaries").nargs("*")
				.help("Filter only addresses inside any of boundary given as osm id. eg. r12345 w123456 ");

			join.addArgument("--skip-in-text").nargs("*")
				.help("Skip in addr full text.");
			
			join.addArgument("--find-langs").setDefault(Boolean.FALSE)
				.nargs("?").setConst(Boolean.FALSE)
				.help("Search for translated address rows. \n"
						+ "Eg. if street and all upper addr levels \n"
						+ "have name name:uk name:ru name:en \n"
						+ "generate 4 address rows.\n"
						+ "If one of [name:uk name:ru name:en] is equals \n"
						+ "to name still generate additional row. \n"
						+ "(You can filter it later with simple distinct check).");
			
		}

		//update
		{
			Command command = Command.SYNCHRONIZE;
			subparsers.addParser(command.longName())
					.setDefault(COMMAND, command)
					.help(command.help());
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
			
			outCSV.addArgument(POI_CATALOG_OPT).setDefault("jar")
				.help("Path to osm-doc catalog xml file. By default internal osm-doc.xml will be used.");
			
			outCSV.addArgument("--line-handler").help("Path to custom groovy line handler.");
			
		}
		
		return parser;
	}

}
