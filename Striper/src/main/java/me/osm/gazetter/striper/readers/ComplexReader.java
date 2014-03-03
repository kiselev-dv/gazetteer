package me.osm.gazetter.striper.readers;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class ComplexReader extends DefaultHandler {

	private List<DefaultHandler> handlers;
	
	public ComplexReader(DefaultHandler... handlers) {
		this.handlers = Arrays.asList(handlers);
	}

	public ComplexReader(List<DefaultHandler> handlers) {
		this.handlers = handlers;
	}

	public void read(InputStream is) {
		try {
			SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();
			saxParser.parse(is, this);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void startElement(String uri, String localName, String qName,
			Attributes attributes) throws SAXException {
		for(DefaultHandler h : handlers) {
			h.startElement(uri, localName, qName, attributes);
		}
	}
	
	@Override
	public void endElement(String uri, String localName, String qName)
			throws SAXException {
		for(DefaultHandler h : handlers) {
			h.endElement(uri, localName, qName);
		}
	}
}
