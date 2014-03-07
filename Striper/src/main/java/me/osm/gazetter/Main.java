package me.osm.gazetter;

import me.osm.gazetter.out.CSVOutConvertor;
import me.osm.gazetter.out.JSONOutConvertor;
import me.osm.gazetter.out.OutWriter;
import me.osm.gazetter.pointlocation.PointLocation;
import me.osm.gazetter.striper.Slicer;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentGroup;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import net.sourceforge.argparse4j.inf.Subparsers;

/**
 * Entry point for executable jar.
 * */
public class Main {
	
	private static final String COMMAND_SLICE = "slice";
	private static final String COMMAND_OUT = "out";
	private static final String COMMAND_JOIN = "join";
	
	private static final String OUT_JSON_VAL = "out_json";
	private static final String OUT_JSON_OPT = "--out-json";
	
	private static final String OUT_HTML_VAL = "out_html";
	private static final String OUT_HTML_OPT = "--out-html";
	
	private static final String OUT_CSV_VAL = "out_csv";
	private static final String OUT_CSV_OPT = "--out-csv";
	
	private static final String SLICE_INPUT = "input";
	
	private static final String JOIN_COMMON_VAL = "common";
	private static final String JOIN_COMMON_OPT = "--common";

	private static final String DATA_DIR_VAL = "data_dir";
	private static final String DATA_DIR_OPT = "--data-dir";
	
	private static final String COMMAND = "command";

	private enum Command {
	    SLICE, JOIN_ADDRESSES, OUT
	};
	
	/**
	 * Parse arguments and run tasks accordingly. 
	 * */
	public static void main(String[] args) {
		
		ArgumentParser parser = getArgumentsParser();
		
		try {
			Namespace namespace = parser.parseArgs(args);
			
			if(namespace.get(COMMAND).equals(Command.SLICE)) {
				Slicer.run(namespace.getString(SLICE_INPUT), namespace.getString(DATA_DIR_VAL));
			}

			if(namespace.get(COMMAND).equals(Command.JOIN_ADDRESSES)) {
				PointLocation.run(namespace.getString(DATA_DIR_VAL), namespace.getString(JOIN_COMMON_VAL));
				
				doWriteOut(namespace);
			}
			
			if(namespace.get(COMMAND).equals(Command.OUT)) {
				doWriteOut(namespace);
			}
			
		} catch (ArgumentParserException e) {
			parser.handleError(e);
		}
		
	}

	/**
	 * Generate arguments parser.
	 * */
	private static ArgumentParser getArgumentsParser() {
		ArgumentParser parser = ArgumentParsers.newArgumentParser("gazetter")
                .defaultHelp(true)
                .description("Create alphabetical index of osm file features");
		
		Subparsers subparsers = parser.addSubparsers();
		
		//slice
		{
			Subparser slice = subparsers.addParser(COMMAND_SLICE).setDefault(COMMAND, Command.SLICE)
					.help("Slice osm data");
			
			addDataDirOpts(slice);
			slice.addArgument(SLICE_INPUT).help("Path to osm file");
		}

		//join
		{
			Subparser joinAddresses = subparsers.addParser(COMMAND_JOIN).setDefault(COMMAND, Command.JOIN_ADDRESSES)
					.help("Join addr points to boundary polygons.")
					.description("By default results will be stored in slices.\n"
							+ "If you need output with formatting as html/csv/json use --out-*");
			addDataDirOpts(joinAddresses);
			
			joinAddresses.addArgument(JOIN_COMMON_OPT).help("Path to json file with features which will be added to all addr nodes.");
			addOutOptions(joinAddresses);
		}

		//out
		{
			Subparser writeOut = subparsers.addParser(COMMAND_OUT).setDefault(COMMAND, Command.OUT)
					.help("Write out result in different formats.");
			
			addDataDirOpts(writeOut);
			addOutOptions(writeOut);
		}
		return parser;
	}

	/**
	 * Add options for data store layer.
	 * */
	private static void addDataDirOpts(Subparser parser) {
		parser.addArgument(DATA_DIR_OPT).help("Path to data store folder.").setDefault("slices");
	}

	/**
	 * Add options for output control.
	 * */
	private static void addOutOptions(Subparser joinAddresses) {
		
		ArgumentGroup otpF = joinAddresses.addArgumentGroup("Output formatting");
		
		otpF.addArgument(OUT_CSV_OPT).dest(OUT_CSV_VAL)
			.action(Arguments.storeConst()).setConst(Boolean.TRUE).setDefault(Boolean.FALSE).help("Print out csv");
		
		otpF.addArgument(OUT_HTML_OPT).dest(OUT_HTML_VAL)
			.action(Arguments.storeConst()).setConst(Boolean.TRUE).help("Print out html");
		
		otpF.addArgument(OUT_JSON_OPT).dest(OUT_JSON_VAL)
			.action(Arguments.storeConst()).setConst(Boolean.TRUE).help("Print out json");
	}

	/**
	 * Parser arguments and
	 * write out results in different formats.
	 * */
	private static void doWriteOut(Namespace namespace) {
		
		//TODO: Add choice stdout or file
		
		if(namespace.get(OUT_CSV_VAL) != null && (Boolean)namespace.get(OUT_CSV_VAL)) {
			new OutWriter(namespace.getString(DATA_DIR_VAL), new CSVOutConvertor()).write();
		}
		else if(namespace.get(OUT_HTML_VAL) != null && (Boolean)namespace.get(OUT_HTML_VAL)) {
			//TODO: Not supported yet
		}
		else if(namespace.get(OUT_JSON_VAL) != null && (Boolean)namespace.get(OUT_JSON_VAL)) {
			new OutWriter(namespace.getString(DATA_DIR_VAL), new JSONOutConvertor()).write();
		}
		
	}
}
