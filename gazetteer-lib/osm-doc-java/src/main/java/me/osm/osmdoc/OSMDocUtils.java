package me.osm.osmdoc;

import java.util.Arrays;

import me.osm.osmdoc.commands.ExpStrings;
import me.osm.osmdoc.commands.FindKeys;
import me.osm.osmdoc.processing.CheckL10n;
import me.osm.osmdoc.processing.Linker;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import net.sourceforge.argparse4j.inf.Subparsers;

import org.apache.commons.lang3.StringUtils;


public class OSMDocUtils {
	
	private static final String COMMAND = "command";
	
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
		
		EXP_STRINGS {
			@Override
			public String longName() {return StringUtils.replaceChars(name().toLowerCase(), "_", "-");}
			@Override
			public String help() {return "Generate csv file with features, "
					+ "tags, etc. names and translations for them";}
		},
		FIND_KEYS {
			@Override
			public String longName() {return StringUtils.replaceChars(name().toLowerCase(), "_", "-");}
			@Override
			public String help() {return "Export i18n keys in separate files by type";}
		},
		COMPILE {
			@Override
			public String longName() {return name().toLowerCase();}
			@Override
			public String help() {return "Compile catalog folder into single xml file";}
		},
		CHECK_L10N {
			@Override
			public String longName() {return name().toLowerCase();}
			@Override
			public String help() {return "Search for missed l10n strings";}
		}

	}

	@SuppressWarnings("unused")
	private static Subparser generateTranslations;
	
	private static Subparser compile;
	
	public static void main(String[] args) {
		
		ArgumentParser parser = getArgumentsParser();
		
		try {
			Namespace namespace = parser.parseArgs(args);
			
			String catalogPath = namespace.getString("catalog");
			
			if(namespace.get(COMMAND).equals(Command.EXP_STRINGS)) {
				new ExpStrings(catalogPath).run();
			}

			if(namespace.get(COMMAND).equals(Command.COMPILE)) {
				Linker.run(namespace.getString("in"), namespace.getString("out"));
			}

			if(namespace.get(COMMAND).equals(Command.FIND_KEYS)) {
				new FindKeys(
						namespace.getString("catalog"), 
						namespace.getString("out"),
						Arrays.asList("ru", "en")
					).run();
			}

			if(namespace.get(COMMAND).equals(Command.CHECK_L10N)) {
				new CheckL10n(namespace.getString("catalog"), 
						namespace.getString("properties")).run();
			}
			
		}
		catch (ArgumentParserException e) {
			parser.handleError(e);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static ArgumentParser getArgumentsParser() {
		ArgumentParser parser = ArgumentParsers.newArgumentParser("osm-doc")
                .defaultHelp(true)
                .description("Various uyility operations for osm-doc treat.");

		parser.version("0.1");
		
		parser.addArgument("--catalog").required(false)
			.help("Path to doc file or folder with catalog features.")
			.setDefault("catalog");
        
        Subparsers subparsers = parser.addSubparsers();
        
        {
        	Command command = Command.EXP_STRINGS;
			generateTranslations = subparsers.addParser(command.longName())
        			.setDefault(COMMAND, command)
					.help(command.help());
        }

        {
        	Command command = Command.COMPILE;
        	compile = subparsers.addParser(command.longName())
        			.setDefault(COMMAND, command)
        			.help(command.help());
        	compile.addArgument("in")
        		.help("Path to folder with catalog");
        	compile.addArgument("out")
        		.help("Path to file where to write the resaults. Use - for stdout.");
        }
        
        {
        	Command command = Command.FIND_KEYS;
        	
        	Subparser findKeys = subparsers.addParser(command.longName())
        			.setDefault(COMMAND, command)
        			.help(command.help());
        	
        	findKeys.addArgument("out")
        		.help("Path to file where to write the resaults.");
        	
        }

        {
        	Command command = Command.CHECK_L10N;
        	
        	Subparser checkL10n = subparsers.addParser(command.longName())
        			.setDefault(COMMAND, command)
        			.help(command.help());
        	
        	checkL10n.addArgument("catalog")
        		.help("Path to osm-doc catalog.");

        	checkL10n.addArgument("properties")
        		.help("Path i18n localization file to check.");
        	
        }
        
        return parser;
	}
	
}
