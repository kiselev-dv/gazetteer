package me.osm.osmdoc.processing;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.lang3.StringUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class Linker extends DefaultHandler{
	
	private PrintWriter writer;
	private Stack<String> path = new Stack<String>();
	
	private StringBuilder textBuffer;
	
	private static Set<String> SKIP = new HashSet<String>(Arrays.asList("doc-part"));
	private static Set<String> ONE_LINE = new HashSet<String>(
			Arrays.asList("title", "key", "icon", "applyed-to", "fref"));
	
	public Linker(PrintWriter printWriter) 
	{
		writer = printWriter;
	}

	public static void run(String catalogPath, String outPath) {
		try {
		
			Linker handler = null;
			if(outPath.equals("-")) {
				handler = new Linker(new PrintWriter(System.out));
			}
			else {
				handler = new Linker(new PrintWriter(new File(outPath)));
			}
			
			SAXParserFactory factory = SAXParserFactory.newInstance();
			SAXParser saxParser = factory.newSAXParser();

			handler.begin();
			
			traverse(handler, saxParser, new File(catalogPath + "/features"));
			traverse(handler, saxParser, new File(catalogPath + "/traits"));
			traverse(handler, saxParser, new File(catalogPath + "/hierarchies"));
			
			
			handler.end();

		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void end() {
		writer.println("</doc-part>");
		
		writer.flush();
		writer.close();
	}

	private void begin() {
		writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n");
		writer.println("<doc-part xmlns=\"http://map.osm.me/osm-doc-part\" xmlns:d=\"http://map.osm.me/osm-doc-part\">");
	}

	private static void traverse(DefaultHandler handler, SAXParser saxParser,
			File dir) throws SAXException, IOException {
		
		for(File f : dir.listFiles()) {
			if (f.isDirectory()) {
				traverse(handler, saxParser, f);
			}
			else if (f.getName().endsWith(".xml")) {
				saxParser.parse(f, handler);
			}
		}
	}
	
	@Override
	public void startElement(String uri, String localName, String qName,
			Attributes attributes) throws SAXException {
		
		echoText();
		
		StringBuilder sb = new StringBuilder();
		
		String elementName = StringUtils.isEmpty(localName) ? qName : localName;
		path.push(elementName);		
		
		sb.append("<").append(elementName);

		if (attributes != null) {
			for (int i = 0; i < attributes.getLength(); i++) {
				sb.append(" ");
				
				String attrName = StringUtils.isEmpty(attributes.getLocalName(i)) ?
						attributes.getQName(i) : attributes.getLocalName(i);
						
				sb.append(attrName);
				
				sb.append("=\"" + attributes.getValue(i) + "\"");
			}
		}
		
		sb.append(">");
		
		writeOpenTag(sb.toString());
	}
	
	@Override
	public void endElement(String uri, String localName, String qName)
			throws SAXException {
		
		echoText();
		
		String tag = "</" + (StringUtils.isEmpty(localName) ? qName : localName) + ">";
		writeCloseTag(tag);
		path.pop();	
	}
	
	public void characters(char buf[], int offset, int len) throws SAXException {
		String s = new String(buf, offset, len);
		if (textBuffer == null) {
			textBuffer = new StringBuilder(s);
		} else {
			textBuffer.append(s);
		}
	}
	
	private void echoText() throws SAXException
	{
	  if (textBuffer == null) 
		  return;
	  
	  writeText(textBuffer.toString());
	  textBuffer = null;
	}
	
	private void writeText(String string) {
		
		if(StringUtils.isNotBlank(string)) {
			String element = path.peek();
			
			if(isOneLine(element)) {
				writer.print(string);
				writer.flush();
				
				return;
			}
			
			writer.println(string);
			writer.flush();
		}
	}

	private void writeOpenTag(String sb) {
		String element = path.peek();
		
		if(SKIP.contains(element)) {
			return;
		}
		
		if(isOneLine(element)) {
			writer.print(indent() + sb);
			writer.flush();
			return;
		}

		writer.println(indent() + sb);
		writer.flush();
	}

	private void writeCloseTag(String sb) {
		String element = path.peek();
		
		if(SKIP.contains(element)) {
			return;
		}

		if(isOneLine(element)) {
			writer.print(sb + System.getProperty("line.separator"));
			writer.flush();
			return;
		}

		writer.println(indent() + sb);
		writer.flush();
	}

	private boolean isOneLine(String element) {
		return ONE_LINE.contains(element) 
				|| (element.equals("trait") && path.contains("feature"));
	}

	private String indent() {
		return StringUtils.repeat('\t', path.size() - 1);
	}
}
