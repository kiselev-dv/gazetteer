package me.osm.gazetter.striper.readers;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class WaysReader extends DefaultHandler {

	private static final String TAG_NAME = "way";
	private WaysHandler[] handlers;
	
	public static class Way {
		public long id;
		public List<Long> nodes = new ArrayList<>();
		public Map<String, String> tags = new HashMap<>();
		public boolean isClosed() {
			return nodes.get(0).equals(nodes.get(nodes.size() - 1));
		}
	}
	
	public static interface WaysHandler {

		void handle(Way line);

	}
	
	private Way line = null;
	private HashSet<String> drop;
	
	public WaysReader(HashSet<String> drop) {
		this.drop = drop;
	}
	
	public void read(InputStream is, WaysHandler... handlers) {
		
		this.handlers = handlers;
		
		try {
			SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();
			saxParser.parse(is, this);
			
		} catch (Exception e) {
			throw new RuntimeException("Parsing failed for: " + is, e);
		}
	}
	
	@Override
	public void startElement(String uri, String localName, String qName,
			Attributes attributes) throws SAXException {
		
		if(qName.equals(TAG_NAME)) {
			this.line = new Way();
			this.line.id = Long.valueOf(attributes.getValue("id"));
		}
		
		if(qName.equals("nd") && this.line != null) {
			this.line.nodes.add(Long.valueOf(attributes.getValue("ref")));
		}
		
		if(qName.equals("tag") && this.line != null) {
			this.line.tags.put(attributes.getValue("k"), attributes.getValue("v"));
		}
		
	}
	
	@Override
	public void endElement(String uri, String localName, String qName)
			throws SAXException {
		if(qName.equals(TAG_NAME)) {
			if(!drop(this.line)) {
				for(WaysHandler handler : handlers) {
					handler.handle(this.line);
				}
			}
			this.line = null;
		}
	}
	
	private final boolean drop(Way w) {
		return !this.drop.isEmpty() && this.drop.contains("w" + w.id);
	}
	
}
