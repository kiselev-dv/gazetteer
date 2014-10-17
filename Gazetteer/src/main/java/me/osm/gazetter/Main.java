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
import me.osm.gazetter.out.Diff;
import me.osm.gazetter.out.GazetteerOutWriter;
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
 */
public class Main {
	
	private static final String BOUNDARIES_FALLBACK_TYPES_PARAM = "--boundaries-fallback-types";
	private static final String BOUNDARIES_FALLBACK_TYPES_VAL = "boundaries_fallback_types";

	private static final String BOUNDARIES_FALLBACK_PARAM = "--boundaries-fallback-file";
	private static final String BOUNDARIES_FALLBACK_VAL = "boundaries_fallback_file";
	
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

	private static final String COMPRESS_VAL = "no_compress";
	private static final String NO_COMPRESS_OPT = "--no-compress";

	private static final String DATA_DIR_VAL = "data_dir";
	private static final String DATA_DIR_OPT = "--data-dir";
	
	private static final String LOG_OPT = "--log-level";
	private static final String LOG_FILE_OPT = "--log-file";

	private static final String POI_CATALOG_VAL = "poi_catalog";
	private static final String POI_CATALOG_OPT = "--poi-catalog";

	private static final String FEATURE_TYPES_VAL = "feature_types";

	private static final String COMMAND = "command";
	
	private static Logger log;
	
	private static Subparser split;
	private static Subparser slice;
	private static Subparser join;
	private static Subparser update;
	private static Subparser outCSV;
	private static Subparser outGazetteer;
	private static Subparser man;
	private static Subparser diff;

	/**
	 * Command line command description
	 * */
	public static interface CommandDescription {
		
		/**
		 * Name of command, will be used as executable.jar long-coomand-name
		 * */
		public String longName();
		
		/**
		 * Command description
		 * */
		public String help(); 
	}

	/**
	 * Supported commands
	 * */
	private enum Command implements CommandDescription {
		
		MAN {
			@Override
			public String longName() {return name().toLowerCase();}
			@Override
			public String help() {return "Prints extended usage";}
		}, 

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
	    },
	    
	    OUT_GAZETTEER {
	    	@Override
	    	public String longName() {return name().toLowerCase().replace('_', '-');}
	    	@Override
	    	public String help() {return "Write data out in json format with gazetter/pelias format.";}
	    },

	    DIFF {
	    	@Override
	    	public String longName() {return name().toLowerCase().replace('_', '-');}
	    	@Override
	    	public String help() {return "Write difference between two gazetteer json files";}
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
			
			String thrds = namespace.get("threads");
			Integer threads = thrds == null ? null : Integer.valueOf(thrds); 
			
			if(namespace.get(COMMAND).equals(Command.JOIN)) {
				Options.initialize(
						AddrLevelsSorting.valueOf(namespace.getString(ADDR_ORDER_VAL)),
						namespace.getString(ADDR_FORMATTER_VAL),
						new HashSet(list(namespace.getList("skip_in_text"))),
						namespace.getBoolean("find_langs")
				);
			}

			if(threads != null) {
				Options.get().setNThreads(threads);
			}

			Options.get().setCompress(namespace.getBoolean(COMPRESS_VAL));

			if(namespace.get(COMMAND).equals(Command.MAN)) {
				printFullHelp(parser);
				System.exit(0);
			}

			if(namespace.get(COMMAND).equals(Command.SPLIT)) {
				File destFolder = new File(namespace.getString(DATA_DIR_VAL));
				String in = namespace.getString("osm_file");
				String compression = namespace.getString("compression");
				Split splitter = new Split(destFolder, in, compression);
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
						list(namespace.getList("drop")),
						namespace.getString(BOUNDARIES_FALLBACK_VAL),
						list(namespace.getList(BOUNDARIES_FALLBACK_TYPES_VAL))
				);
				
			}

			if(namespace.get(COMMAND).equals(Command.JOIN)) {
				
				new Joiner(new HashSet(list(namespace.getList("check_boundaries"))))
					.run(namespace.getString(DATA_DIR_VAL), namespace.getString(JOIN_COMMON_VAL));
				
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

			if(namespace.get(COMMAND).equals(Command.OUT_GAZETTEER)) {
				
				new GazetteerOutWriter(
						namespace.getString(DATA_DIR_VAL), 
						namespace.getString("out_file"),
						namespace.getString(POI_CATALOG_VAL),
						list(namespace.getList("local_admin")),
						list(namespace.getList("locality")),
						list(namespace.getList("neighborhood")),
						namespace.getBoolean("all_names")).write();
			}

			if(namespace.get(COMMAND).equals(Command.DIFF)) {
				
				List<String> in = list(namespace.getList("files"));
				
				new Diff(in.get(0), in.get(1), namespace.getString("out_file")).run();
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

	private static void printFullHelp(ArgumentParser parser) {
		
		parser.printHelp();
		
		System.out.print("\nGazetteer version: ");
		System.out.print(Versions.gazetteer);
		System.out.print("\n\n");
		
		System.out.print("\nCommands:\n\n");
		
		System.out.print("MAN\n\n");
		man.printHelp();
		
		System.out.print("\n\n\nSPLIT\n\n");
		split.printHelp();
		
		System.out.print("\n\n\nSLICE\n\n");
		slice.printHelp();
		
		System.out.print("\n\n\nJOIN\n\n");
		join.printHelp();

		System.out.print("\n\n\nUPDATE\n\n");
		update.printHelp();

		System.out.print("\n\n\nOUT CSV\n\n");
		outCSV.printHelp();
		
		System.out.print("\n\n\nOUT GAZETTEER\n\n");
		outGazetteer.printHelp();

		System.out.print("\n\n\nDIFF\n\n");
		diff.printHelp();
	}

	/**
	 * Returns string list or empty list for null 
	 * */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static List<String> list( List list) {
		if(list == null) {
			return Collections.emptyList();
		}
		return list;
	}

	/**
	 * Initialize logging system.
	 * <p>
	 * Logging options should be set before any logger will be instantiated.
	 * */
	private static void initLog(String[] args) {
		System.setProperty(org.slf4j.impl.SimpleLogger.SHOW_DATE_TIME_KEY, "true");
		System.setProperty(org.slf4j.impl.SimpleLogger.DATE_TIME_FORMAT_KEY, "yyyy-MM-dd HH.mm.ss.S");
		System.setProperty(org.slf4j.impl.SimpleLogger.SHOW_SHORT_LOG_NAME_KEY, "true");

		Iterator<String> iterator = Arrays.asList(args).iterator();
		while(iterator.hasNext()) {
			String k = iterator.next();
			if(k.equals(LOG_OPT) && iterator.hasNext()) {
				System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, iterator.next());
			}
			else if(k.equals(LOG_FILE_OPT) && iterator.hasNext()) {
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

		parser.version(Versions.gazetteer);
		
		parser.addArgument("--threads").required(false).help("set number of threads avaible");
		
		parser.addArgument(NO_COMPRESS_OPT).required(false)
			.help("Do not cmpress tepmlorary stored data").setDefault(true).setConst(false).nargs("?");
		
        parser.addArgument(DATA_DIR_OPT).required(false).
                help("Use folder as data storage.").setDefault("data");
        
        parser.addArgument(LOG_OPT).required(false).setDefault("WARN");
        parser.addArgument(LOG_FILE_OPT).required(false);
        
        Subparsers subparsers = parser.addSubparsers();
		
        //man
        {
        	Command command = Command.MAN;
        	man = subparsers.addParser(command.longName())
        			.setDefault(COMMAND, command)
        			.help(command.help());
        }

        //split
        {
        	Command command = Command.SPLIT;
			split = subparsers.addParser(command.longName())
        			.setDefault(COMMAND, command)
					.help(command.help());
        	
        	split.addArgument("osm_file").required(true)
        		.help("Path to osm file. *.osm *.osm.bz2 *.osm.gz supported. Use - to read from STDIN");
        	
        	split.addArgument("compression").required(false).nargs("?").choices("none", "gzip", "bz2")
        		.setConst("none").setDefault("bz2")
        		.help("Use with \"osm_file -\" allow to read compressed stream from STDIN.");
        	
        }
        
		//slice
		{
			Command command = Command.SLICE;
			slice = subparsers.addParser(command.longName())
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
			
			slice.addArgument(BOUNDARIES_FALLBACK_PARAM).nargs("?")
				.help("Path to boundaries fallback file.");
			
			slice.addArgument(BOUNDARIES_FALLBACK_TYPES_PARAM).nargs("*")
				.help("List of boundaries to keep in boundaries fallback file. Eg. boundary:2");
			
		}

		//join
		{
			Command command = Command.JOIN;
			join = subparsers.addParser(command.longName())
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
				.nargs("?").setConst(Boolean.TRUE)
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
			update = subparsers.addParser(command.longName())
					.setDefault(COMMAND, command)
					.help(command.help());
		}

		//out-csv
		{
			Command command = Command.OUT_CSV;
			outCSV = subparsers.addParser(command.longName())
        			.setDefault(COMMAND, command)
					.help(command.help());
			
			outCSV.addArgument("--columns").nargs("+");
			outCSV.addArgument("--types").nargs("+").choices(CSVOutWriter.ARG_TO_TYPE.keySet());
			outCSV.addArgument("--out-file").setDefault("-");
			
			outCSV.addArgument(POI_CATALOG_OPT).setDefault("jar")
				.help("Path to osm-doc catalog xml file. By default internal osm-doc.xml will be used.");
			
			outCSV.addArgument("--line-handler").help("Path to custom groovy line handler.");
			
		}

		//out-gazetteer
		{
			Command command = Command.OUT_GAZETTEER;
			outGazetteer = subparsers.addParser(command.longName())
					.setDefault(COMMAND, command)
					.help(command.help());
			
			outGazetteer.addArgument("--out-file").setDefault("-");
			
			outGazetteer.addArgument(POI_CATALOG_OPT).setDefault("jar")
				.help("Path to osm-doc catalog xml file. By default internal osm-doc.xml will be used.");
			
			outGazetteer.addArgument("--local-admin").nargs("*")
				.help("Addr levels for local administrations.");
			outGazetteer.addArgument("--locality").nargs("*")
				.help("Addr levels for locality.");
			outGazetteer.addArgument("--neighborhood").nargs("*")
				.help("Addr levels for neighborhood.");
			
			outGazetteer.addArgument("--all-names").setDefault(false).setConst(true)
				.help("Add hash with all *name* tags.");
			
		}
		
		//diff
		{
			Command command = Command.DIFF;
			diff = subparsers.addParser(command.longName())
					.setDefault(COMMAND, command)
					.help(command.help());
			
			diff.addArgument("--out-file").setDefault("-");
			diff.addArgument("--files").nargs(2);
			
		}
		
		return parser;
	}

}
