package me.osm.gazetter;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import javax.sound.midi.SysexMessage;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.osm.gazetter.addresses.AddrLevelsSorting;
import me.osm.gazetter.diff.Diff;
import me.osm.gazetter.join.JoinExecutor;
import me.osm.gazetter.sortupdate.SortUpdate;
import me.osm.gazetter.split.Split;
import me.osm.gazetter.striper.Slicer;
import me.osm.gazetter.tilebuildings.TileBuildings;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.impl.action.StoreTrueArgumentAction;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import net.sourceforge.argparse4j.inf.Subparsers;

/**
 * Entry point for executable jar.
 */
public class Gazetteer {
	
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
	private static final String LOG_PREFIX_OPT = "--log-prefix";
	private static final String LOG_FILE_ONLY = "--log-console-mute";

	private static final String POI_CATALOG_VAL = "poi_catalog";
	private static final String POI_CATALOG_OPT = "--poi-catalog";

	private static final String FEATURE_TYPES_VAL = "feature_types";

	private static final String COMMAND = "command";
	
	
	private static Logger log;
	
	private static Subparser split;
	private static Subparser slice;
	private static Subparser join;
	private static Subparser update;
	private static Subparser man;
	private static Subparser diff;
	private static Subparser tileBuildings;

	/**
	 * Command line command description
	 * */
	public static interface CommandDescription {
		
		/**
		 * Name of command, will be used as executable.jar long-coomand-name
		 * 
		 * @return long name
		 * */
		public String longName();
		
		/**
		 * Command description
		 * 
		 * @return help string
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
			public String help() {return "Print detailed usage.";}
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
	    
	    DIFF {
	    	@Override
	    	public String longName() {return name().toLowerCase().replace('_', '-');}
	    	@Override
	    	public String help() {return "Write difference between two gazetteer json files.";}
	    },

	    MATCH_FLAP {
	    	@Override
	    	public String longName() {return name().toLowerCase().replace('_', '-');}
	    	@Override
	    	public String help() {return "Match features with flap objects.";}
	    },
		
		TILE_BUILDINGS {
	    	@Override
	    	public String longName() {return name().toLowerCase().replace('_', '-');}
	    	@Override
	    	public String help() {return "Export building and building:part objects into tiles.";}
	    };

	};

	/**
	 * Parse arguments and run tasks accordingly.
	 * 
	 * @param args
	 *          Command line arguments
	 * */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static void main(String[] args) {
		
		initLog(args);
		log = LoggerFactory.getLogger(Gazetteer.class);
		
		
		ArgumentParser parser = getArgumentsParser();

		if(args.length > 0 && ("-v".equals(args[0]) || "--version".equals(args[0]))) {
			printVersion("--version".equals(args[0]));
			return;
		}
		
		try {
			Namespace namespace = parser.parseArgs(args);
			
			if(namespace.getBoolean("version")) {
				printVersion(true);
				return;
			}
			
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

			String data_dir = namespace.getString(DATA_DIR_VAL);
			if(namespace.get(COMMAND).equals(Command.SPLIT)) {
				File destFolder = new File(data_dir);
				String in = namespace.getString("osm_file");
				String compression = namespace.getString("compression");
				boolean append = namespace.getBoolean("append");
				Split splitter = new Split(destFolder, in, compression, append);
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
				
				new Slicer(data_dir).run(
						namespace.getString(POI_CATALOG_VAL), 
						types,
						list(namespace.getList(EXCCLUDE_POI_BRANCH_VAL)),
						list(namespace.getList(NAMED_POI_BRANCH_VAL)),
						list(namespace.getList("drop")),
						namespace.getString(BOUNDARIES_FALLBACK_VAL),
						list(namespace.getList(BOUNDARIES_FALLBACK_TYPES_VAL)),
						namespace.getBoolean("x10"),
						namespace.getBoolean("skip_interpolation"),
						namespace.getBoolean("disk_index"),
						!namespace.getBoolean("skip_point_to_boundary_merge"),
						!namespace.getBoolean("skip_nearest_city")
				);
				
			}

			if(namespace.get(COMMAND).equals(Command.JOIN)) {
				
				List<String> handlers = list(namespace.getList("handlers"));
				Options.get().setJoinHandlers(handlers);
				
				if(Options.get().getJoinOutHandlers().isEmpty()) {
					System.out.println("No join handlers was initialized.");
					System.out.println("Predefined handlers are: " + StringUtils.join(Options.getPredefinedOutHandlers(), ", "));
					System.exit(1);
				}
				
				new JoinExecutor(namespace.getBoolean("skip_hghnets"), 
						namespace.getBoolean("keep_hghnets_geometry"),
						namespace.getBoolean("clean_stripes"),
						new HashSet(list(namespace.getList("check_boundaries")))).run(
								data_dir, 
								namespace.getString(JOIN_COMMON_VAL));
				
			}

			if(namespace.get(COMMAND).equals(Command.SYNCHRONIZE)) {
				new SortUpdate(data_dir).run();
			}
			
			if(namespace.get(COMMAND).equals(Command.DIFF)) {
				Boolean full = namespace.getBoolean("--full");
				full = full == null ? false : full;
				
				Diff diffExecutor = new Diff(namespace.getString("old"), 
						namespace.getString("new"), 
						namespace.getString("out_file"), 
						full,
						namespace.getBoolean("disk_index"),
						namespace.getBoolean("only_key_length"));
				
				diffExecutor.run();
			}
			
			if(namespace.get(COMMAND).equals(Command.MATCH_FLAP)) {
				log.info("Not implemented, exit");
			}
			
			if(namespace.get(COMMAND).equals(Command.TILE_BUILDINGS)) {
				File destFolder = new File(namespace.getString("out_dir"));
				File dataDir = new File(namespace.getString(DATA_DIR_VAL));
				Integer lvl = Integer.valueOf(namespace.getString("level"));
				if (lvl == null || lvl < 0 || lvl > 22) {
					log.info("Use default level 13");
					lvl = 13;
				}
				boolean diskIndex = namespace.getBoolean("disk_index");
				List<String> drop = list(namespace.getList("drop"));
				
				TileBuildings tiler = new TileBuildings(
						dataDir, lvl, destFolder, drop, diskIndex);
				tiler.run();
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

	/**
	 * Print version of gazetteer to stdout
	 *
	 * @param full print versions of major dependencies
	 * */
	private static void printVersion(boolean full) {
		
		if(full) {
			System.out.println("Gazetteer: " + Versions.gazetteer);
			System.out.println("Build timestamp: " + Versions.buildTs);
			System.out.println("Java Topology Syte: " + Versions.jts);
			System.out.println("Osm Doc Java: " + Versions.osmdoc);
			System.out.println("Groovy runtime: " + Versions.groovy);
		}
		else {
			System.out.println(Versions.gazetteer);
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

		System.out.print("\n\n\nDIFF\n\n");
		diff.printHelp();
		
		System.out.print("\n\n\nDIFF\n\n");
		tileBuildings.printHelp();
	}

	/**
	 * Returns string list or empty list for null
	 * 
	 * @param list
	 * 		unsafe list
	 * @return List of strings
	 * */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static List<String> list( List list) {
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

		/**
		 * XXX: Static not final access to LogbackConfigurator is a crap
		 * 
		 * Done on purpose, logback use LogbackConfigurator 
		 * binded via META-INF/services. And I don't want to move it inside
		 * Gazetteer class, because I don't know when it will be created and
		 * accessed. So I need a way to set that fields. 
		 * 
		 * From the other hand it just wanted to work the right way, thats
		 * why I've made ugly configureStatic which completly unnecessary
		 * if services providesrs works
		 * */
		Iterator<String> iterator = Arrays.asList(args).iterator();
		while(iterator.hasNext()) {
			String k = iterator.next();
			if(k.equals(LOG_OPT) && iterator.hasNext()) {
				LogbackConfigurator.level = iterator.next();
			}
			else if(k.equals(LOG_FILE_OPT) && iterator.hasNext()) {
				LogbackConfigurator.outFile = iterator.next();
			}
			else if(k.equals(LOG_PREFIX_OPT) && iterator.hasNext()) {
				LogbackConfigurator.logPrefix = iterator.next();
			}
			else if(k.equals(LOG_FILE_ONLY)) {
				LogbackConfigurator.muteConsole = true;
			}
		}
		
		LogbackConfigurator.configureStatic();
	}

	/**
	 * Generate arguments parser.
	 * */
	private static ArgumentParser getArgumentsParser() {
		ArgumentParser parser = ArgumentParsers.newArgumentParser("gazetter")
                .defaultHelp(true)
                .description("Create alphabetical index of osm file features.");

		parser.version(Versions.gazetteer);
		
		parser.addArgument("--threads").required(false)
			.help("set number of threads avaible. By default will be used runtime.availableProcessors value.");
		
		parser.addArgument(NO_COMPRESS_OPT).required(false).action(Arguments.storeFalse())
			.help("Do not cmpress tepmlorary stored data")
			.setDefault(Boolean.TRUE);
		
        parser.addArgument(DATA_DIR_OPT).required(false)
        	.help("Use this folder as data storage.")
        	.setDefault("data");
        
        parser.addArgument(LOG_OPT).required(false).setDefault("WARN");
        parser.addArgument(LOG_FILE_OPT).required(false).help("Path to log file");
        parser.addArgument(LOG_PREFIX_OPT).required(false).help("Add that prefix to all log messages");
        parser.addArgument(LOG_FILE_ONLY).required(false)
        	.action(Arguments.storeTrue())
        	.setDefault(Boolean.TRUE)
        	.help("Mute console output");

        parser.addArgument("--version", "-v").required(false)
        	.help("Print version and exit.")
        	.action(Arguments.storeTrue())
        	.setDefault(Boolean.FALSE);
        
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
        	
        	split.addArgument("--append").required(false).setDefault(Boolean.FALSE)
				.nargs("?").setConst(Boolean.TRUE);
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
			
			slice.addArgument("--x10").setConst(Boolean.TRUE)
				.setDefault(Boolean.FALSE).action(new StoreTrueArgumentAction())
				.help("Slice ten times thinner stripes");
			
			slice.addArgument("--skip-interpolation").setConst(Boolean.TRUE)
				.setDefault(Boolean.FALSE).action(new StoreTrueArgumentAction())
				.help("Do not parse addr:interpolation lines");
			
			slice.addArgument("--disk-index").setConst(Boolean.TRUE)
				.setDefault(Boolean.FALSE).action(new StoreTrueArgumentAction())
				.help("Use off RAM index for points/ways/relations build");
			
			slice.addArgument("--skip-point-to-boundary-merge").setConst(Boolean.TRUE)
				.setDefault(Boolean.FALSE).action(new StoreTrueArgumentAction())
				.help("Don't merge place point to it's boundary");
			
			slice.addArgument("--skip-nearest-city").setConst(Boolean.TRUE)
				.setDefault(Boolean.FALSE).action(new StoreTrueArgumentAction())
				.help("Use only polygonal boundaries for cities");
			
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
			
			join.addArgument("--skip-hghnets").setConst(Boolean.TRUE)
				.setDefault(Boolean.FALSE).action(new StoreTrueArgumentAction())
				.help("Do not build highway networks.");

			join.addArgument("--clean-stripes").setConst(Boolean.TRUE)
				.setDefault(Boolean.FALSE).action(new StoreTrueArgumentAction())
				.help("Remove stripes intermediate files, right after usage");

			join.addArgument("--keep-hghnets-geometry").setConst(Boolean.TRUE)
				.setDefault(Boolean.FALSE).action(new StoreTrueArgumentAction())
				.help("Do not drop highway networks geometries.");

			join.addArgument("--handlers").nargs("*");
			
			
		}

		//update
		{
			Command command = Command.SYNCHRONIZE;
			update = subparsers.addParser(command.longName())
					.setDefault(COMMAND, command)
					.help(command.help());
		}
		
		//diff
		{
			Command command = Command.DIFF;
			diff = subparsers.addParser(command.longName())
					.setDefault(COMMAND, command)
					.help(command.help());
			
			diff.addArgument("--out-file").setDefault("-")
				.help("Where to print results.");
			
			diff.addArgument("--old").required(true)
				.help("Path to old file.");
			diff.addArgument("--new").required(true)
				.help("Path to new file.");
			
			diff.addArgument("--full").setConst(Boolean.TRUE)
				.setDefault(Boolean.FALSE).action(new StoreTrueArgumentAction())
				.help("Print full object data for deleted and old rows.");
			
			diff.addArgument("--disk-index").setConst(Boolean.TRUE)
				.setDefault(Boolean.FALSE).action(new StoreTrueArgumentAction())
				.help("Print full object data for deleted and old rows.");
		
			diff.addArgument("--only-key-length").setConst(Boolean.TRUE)
				.setDefault(Boolean.FALSE).action(new StoreTrueArgumentAction())
				.help("Print full object data for deleted and old rows.");
			
		}
		
		{
			Command command = Command.TILE_BUILDINGS;
			tileBuildings = subparsers.addParser(command.longName())
        			.setDefault(COMMAND, command)
					.help(command.help());
			
			tileBuildings.addArgument("--drop").nargs("*")
				.help("List of objects osm ids which will be dropped ex r60189.");
			
			tileBuildings.addArgument("--disk-index").setConst(Boolean.TRUE)
				.setDefault(Boolean.FALSE).action(new StoreTrueArgumentAction())
				.help("Do not parse addr:interpolation lines");
			
			tileBuildings.addArgument("--out-dir").setDefault("tiles")
				.help("Where to print results.");
			
			tileBuildings.addArgument("--level").setDefault("12")
				.help("Zoom level for generated tiles.");
			
			
		}
		
		return parser;
	}

}
