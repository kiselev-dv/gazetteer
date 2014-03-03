package me.osm.gazetter;

import me.osm.gazetter.pointlocation.PointLocation;
import me.osm.gazetter.striper.Slicer;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import net.sourceforge.argparse4j.inf.Subparsers;

public class Main {
	private enum Command {
	    SLICE, JOIN_ADDRESSES
	};
	
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
				.help("Join addr points to boundary polygons and write out result.");
		joinAddresses.addArgument("--slices-dir").help("Path to slices dir").setDefault("slices");
		
		joinAddresses.addArgument("--common").help("Path to json file with features which will be added to all addr nodes.");
		
		try {
			Namespace namespace = parser.parseArgs(args);
			
			if(namespace.get("command").equals(Command.SLICE)) {
				Slicer.run(namespace.getString("input"), namespace.getString("slices_dir"));
			}

			if(namespace.get("command").equals(Command.JOIN_ADDRESSES)) {
				PointLocation.run(namespace.getString("slices_dir"), namespace.getString("common"));
			}
			
		} catch (ArgumentParserException e) {
			parser.handleError(e);
		}
		
	}
}
