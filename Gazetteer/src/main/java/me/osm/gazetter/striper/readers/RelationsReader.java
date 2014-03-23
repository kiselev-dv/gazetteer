package me.osm.gazetter.striper.readers;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import me.osm.gazetter.striper.readers.RelationsReader.Relation.RelationMember;
import me.osm.gazetter.striper.readers.RelationsReader.Relation.RelationMember.ReferenceType;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class RelationsReader extends DefaultHandler {

	private static final String TAG_NAME = "relation";
	private RelationsHandler[] handlers;
	
	public static class Relation {
		public long id;
		public List<RelationMember> members = new ArrayList<>();
		public Map<String, String> tags = new HashMap<>();
		
		public static class RelationMember
		{
			public long ref;
			public String role;
			public ReferenceType type;
			
			public static enum ReferenceType {
				NODE, WAY, RELATION
			}
		}
	}
	
	public static interface RelationsHandler {

		void handle(Relation rel);

	}
	
	public RelationsReader(){
		
	};
	
	public RelationsReader(RelationsHandler... handlers) {
		this.handlers = handlers;
	}
	
	private Relation relation = null;
	
	public void read(InputStream is, RelationsHandler... handlers) {
		
		this.handlers = handlers;
		
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
		
		if(qName.equals(TAG_NAME)) {
			this.relation = new Relation();
			this.relation.id = Long.valueOf(attributes.getValue("id"));
		}
		
		if(qName.equals("member") && this.relation != null) {
			RelationMember m = new RelationMember();
			m.ref = Long.valueOf(attributes.getValue("ref"));
			m.type = ReferenceType.valueOf(attributes.getValue("type").toUpperCase());
			if(!attributes.getValue("role").isEmpty()) {
				m.role = attributes.getValue("role");
			}
			this.relation.members.add(m);
		}
		
		if(qName.equals("tag") && this.relation != null) {
			this.relation.tags.put(attributes.getValue("k"), attributes.getValue("v"));
		}
		
	}
	
	@Override
	public void endElement(String uri, String localName, String qName)
			throws SAXException {
		if(qName.equals(TAG_NAME)) {
			for(RelationsHandler handler : handlers) {
				handler.handle(this.relation);
			}
			this.relation = null;
		}
	}
	
}
