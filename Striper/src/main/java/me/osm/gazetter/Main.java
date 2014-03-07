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

public class Main {
	private enum Command {
	    SLICE, JOIN_ADDRESSES, OUT
	};
	
	/**
	 * Parse arguments and run tasks accordingly. 
	 * */
	public static void main(String[] args) {
		ArgumentParser parser = ArgumentParsers.newArgumentParser("gazetter")
                .defaultHelp(true)
                .description("Create alphabetical index of osm file features");
		
		Subparsers subparsers = parser.addSubparsers();
		
		Subparser slice = subparsers.addParser("slice").setDefault("command", Command.SLICE)
				.help("Slice osm data");
		
		slice.addArgument("--slices-dir").help("Directory for storing slices.").setDefault("slices");
		slice.addArgument("input").help("Path to osm file");
		
		Subparser joinAddresses = subparsers.addParser("join").setDefault("command", Command.JOIN_ADDRESSES)
				.help("Join addr points to boundary polygons.")
				.description("By default results will be stored in slices.\n"
						   + "If you need output with formatting as html/csv/json use --out-*");
		joinAddresses.addArgument("--slices-dir").help("Path to slices dir").setDefault("slices");
		
		joinAddresses.addArgument("--common").help("Path to json file with features which will be added to all addr nodes.");
		addOutOptions(joinAddresses);
		
		Subparser writeOut = subparsers.addParser("out").setDefault("command", Command.OUT)
				.help("Write out result in different formats.");

		writeOut.addArgument("--slices-dir").help("Path to slices dir").setDefault("slices");
		addOutOptions(writeOut);
		
		try {
			Namespace namespace = parser.parseArgs(args);
			
			if(namespace.get("command").equals(Command.SLICE)) {
				Slicer.run(namespace.getString("input"), namespace.getString("slices_dir"));
			}

			if(namespace.get("command").equals(Command.JOIN_ADDRESSES)) {
				PointLocation.run(namespace.getString("slices_dir"), namespace.getString("common"));
				
				doWriteOut(namespace);
			}
			
			if(namespace.get("command").equals(Command.OUT)) {
				doWriteOut(namespace);
			}
			
		} catch (ArgumentParserException e) {
			parser.handleError(e);
		}
		
	}

	private static void addOutOptions(Subparser joinAddresses) {
		
		ArgumentGroup otpF = joinAddresses.addArgumentGroup("Output formatting");
		otpF.addArgument("--out-csv").dest("out_csv")
			.action(Arguments.storeConst()).setConst(Boolean.TRUE).setDefault(Boolean.FALSE).help("Print out csv");
		otpF.addArgument("--out-html").dest("out_html")
			.action(Arguments.storeConst()).setConst(Boolean.TRUE).help("Print out html");
		otpF.addArgument("--out-json").dest("out_json")
			.action(Arguments.storeConst()).setConst(Boolean.TRUE).help("Print out json");
	}

	/**
	 * Write out results in different formats.
	 * */
	private static void doWriteOut(Namespace namespace) {
		
		//TODO: Add choice stdout or file
		
		if(namespace.get("out_csv") != null && (Boolean)namespace.get("out_csv")) {
			new OutWriter(namespace.getString("slices_dir"), new CSVOutConvertor()).write();
		}
		else if(namespace.get("out_html") != null && (Boolean)namespace.get("out_html")) {
			//TODO: Not supported yet
		}
		else if(namespace.get("out_json") != null && (Boolean)namespace.get("out_json")) {
			new OutWriter(namespace.getString("slices_dir"), new JSONOutConvertor()).write();
		}
		
	}
}
