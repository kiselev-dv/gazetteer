package me.osm.gazetteer.striper.readers;

import java.io.InputStream;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.lang3.StringUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class PointsReader extends DefaultHandler {

	private PointsHandler[] handlers;
	private HashSet<String> drop;

	private Timestamp lastNodeTimestamp = null;

	public static class Node {
		public long id;
		public double lon;
		public double lat;
		public Map<String, String> tags = new HashMap<>();
	}

	public static interface PointsHandler {

		public void handle(Node node);

	}

	public PointsReader(HashSet<String> drop) {
		this.drop = drop;
	}

	private Node node = null;

	public void read(InputStream is, PointsHandler... handlers) {

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
		if(qName.equals("node")) {
			this.node = new Node();
			this.node.id = Long.valueOf(attributes.getValue("id"));
			this.node.lon = Double.valueOf(attributes.getValue("lon"));
			this.node.lat = Double.valueOf(attributes.getValue("lat"));

			String tsRaw = attributes.getValue("timestamp");
			if (StringUtils.isNotBlank(tsRaw)) {
				Timestamp ts = Timestamp.valueOf(StringUtils.remove(tsRaw, 'Z').replace('T', ' '));
				if(lastNodeTimestamp == null || ts.after(lastNodeTimestamp)) {
					lastNodeTimestamp = ts;
				}
			}
		}

		if(qName.equals("tag") && this.node != null) {
			this.node.tags.put(attributes.getValue("k"), attributes.getValue("v"));
		}
	}

	@Override
	public void endElement(String uri, String localName, String qName)
			throws SAXException {
		if(qName.equals("node")) {
			if(!drop(this.node)) {
				for(PointsHandler handler : handlers) {
					handler.handle(this.node);
				}
			}
			this.node = null;
		}
	}

	private final boolean drop(Node n) {
		return !this.drop.isEmpty() && this.drop.contains("n" + n.id);
	}

	public Timestamp getLastNodeTimestamp() {
		return lastNodeTimestamp;
	}

}
