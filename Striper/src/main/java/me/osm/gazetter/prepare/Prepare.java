package me.osm.gazetter.prepare;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Date;

import me.osm.gazetter.utils.FileUtils;
import me.osm.gazetter.utils.FileUtils.LineHandler;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Prepare implements LineHandler {
	
	private String input;
	
	private static final Logger log = LoggerFactory.getLogger(Prepare.class);
	
	private static final String HEADER = "<?xml version='1.0' encoding='UTF-8'?>";

	public static void main(String[] args) {
		long start = new Date().getTime();
		new Prepare(new File(args[0]), args[1]).run();
		log.info("Slice done in {}", DurationFormatUtils.formatDurationHMS(new Date().getTime() - start));
	}
	
	private PrintWriter nodePW;
	private PrintWriter wayPW;
	private PrintWriter relPW;
	
	public Prepare (File destFolder, String input) {
		this.input = input;
		destFolder.mkdirs();
		try {
			nodePW = new PrintWriter(new File(destFolder.getAbsolutePath() + "/" + "nodes.osm"));
			wayPW = new PrintWriter(new File(destFolder.getAbsolutePath() + "/" + "ways.osm"));
			relPW = new PrintWriter(new File(destFolder.getAbsolutePath() + "/" + "rels.osm"));
			
			nodePW.println(HEADER);
			nodePW.println("<osm>");
			
			wayPW.println(HEADER);
			wayPW.println("<osm>");
			
			relPW.println(HEADER);
			relPW.println("<osm>");
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void run() {

		try {
			InputStream fileIS = FileUtils.getFileIS(input);
			FileUtils.handleLines(fileIS, this);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		done();
	}

	private void done() {
		nodePW.println("</osm>");
		
		wayPW.println("</osm>");
		
		relPW.println("</osm>");

		nodePW.flush();
		wayPW.flush();
		relPW.flush();

		nodePW.close();
		wayPW.close();
		relPW.close();
	}

	private boolean insideNode = false;
	private boolean insideWay = false;
	private boolean insideRelation = false;
	
	@Override
	public void handle(String line) {

		String s = StringUtils.strip(line);
		
		//node
		{
			if(s.startsWith("<node ")) {
				writeNode(line);
				insideNode = true;
				if(s.endsWith("/>")) {
					insideNode = false;
				}
				return;
			}
			
			if(s.startsWith("</node>")) {
				writeNode(line);
				insideNode = false;
				return;
			}
			
			if(insideNode) {
				writeNode(line);
				return;
			}
		}

		//way
		{
			if(s.startsWith("<way ")) {
				writeWay(line);
				insideWay = true;
				if(s.endsWith("/>")) {
					insideWay = false;
				}
				return;
			}
			
			if(s.startsWith("</way>")) {
				writeWay(line);
				insideWay = false;
				return;
			}
			
			if(insideWay) {
				writeWay(line);
				return;
			}
		}

		//relation
		{
			if(s.startsWith("<relation ")) {
				writeRel(line);
				insideRelation = true;
				if(s.endsWith("/>")) {
					insideRelation = false;
				}
				return;
			}
			
			if(s.startsWith("</relation>")) {
				writeRel(line);
				insideRelation = false;
				return;
			}
			
			if(insideRelation) {
				writeRel(line);
				return;
			}
		}
	}

	private void writeRel(String s) {
		relPW.println(s);
	}

	private void writeWay(String s) {
		wayPW.println(s);
	}

	private void writeNode(String s) {
		nodePW.println(s);
	}
	
}
