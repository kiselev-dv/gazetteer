package me.osm.gazetteer.tilebuildings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.lang3.StringUtils;

import com.sun.xml.txw2.output.IndentingXMLStreamWriter;
import com.vividsolutions.jts.geom.Envelope;

import gnu.trove.set.hash.TLongHashSet;
import me.osm.gazetteer.Versions;
import me.osm.gazetteer.striper.readers.RelationsReader.Relation;
import me.osm.gazetteer.striper.readers.RelationsReader.Relation.RelationMember;

public class OSMXMLWriter {

	private XMLStreamWriter out;
	private TLongHashSet writtenNodes = new TLongHashSet();
	private TLongHashSet writtenWays = new TLongHashSet();
	private OutputStream os;
	private boolean closed = false;

	public OSMXMLWriter (File outFile) {
		try {
			outFile.getParentFile().mkdirs();
			os = new FileOutputStream(outFile);

			this.out = XMLOutputFactory.newInstance().createXMLStreamWriter(
					new OutputStreamWriter(os, "utf-8"));
			this.out = new IndentingXMLStreamWriter(this.out);

			out.writeStartDocument();

			// OSM
			out.writeStartElement("osm");
			out.writeAttribute("version", "0.6");
			out.writeAttribute("generator", "OSM-Gazetteer-tiler " + Versions.gazetteer);

		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void writeBounds(Envelope bbox) {
		try {
			out.writeStartElement("bounds");
			out.writeAttribute("minlat", formatDouble(bbox.getMinY()));
			out.writeAttribute("minlon", formatDouble(bbox.getMinX()));
			out.writeAttribute("maxlat", formatDouble(bbox.getMaxY()));
			out.writeAttribute("maxlon", formatDouble(bbox.getMaxX()));
			out.writeEndElement();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private String formatDouble(double d) {
		return String.format("%.7f", d).replace(',', '.');
	}

	public void close() {
		if (!this.closed) {
			try {
				out.writeEndElement();
				out.writeEndDocument();

				out.flush();
				os.flush();

				out.close();
				this.closed = true;
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

	public void writeNode(long id, double lon, double lat, Map<String, String> tags) {
		if (writtenNodes.contains(id)) {
			return;
		}

		writtenNodes.add(id);

		try {
			out.writeStartElement("node");

			out.writeAttribute("id", String.valueOf(id));
			out.writeAttribute("lon", formatDouble(lon));
			out.writeAttribute("lat", formatDouble(lat));

			writeFakeAttributes();

			if (tags != null && !tags.isEmpty()) {
				writeTags(tags);
			}

			out.writeEndElement();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void writeTags(Map<String, String> tags) throws XMLStreamException {
		for(Entry<String, String> tag : tags.entrySet()) {
			out.writeStartElement("tag");
			out.writeAttribute("k", tag.getKey());
			out.writeAttribute("v", tag.getValue());
			out.writeEndElement();
		}
	}

	public void writeWay(long id, List<Long> nodes, Map<String, String> tags) {

		if (writtenWays.contains(id) || nodes == null || nodes.isEmpty()) {
			return;
		}

		writtenWays.add(id);

		try {
			out.writeStartElement("way");

			out.writeAttribute("id", String.valueOf(id));

			writeFakeAttributes();

			for(Long n : nodes) {
				writeND(n);
			}

			if (tags != null && !tags.isEmpty()) {
				writeTags(tags);
			}

			out.writeEndElement();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void writeND(Long n) throws XMLStreamException {
		out.writeStartElement("nd");
		out.writeAttribute("ref", String.valueOf(n));
		out.writeEndElement();
	}

	public void writeRelation(Relation rel) {
		try {
			out.writeStartElement("relation");

			out.writeAttribute("id", String.valueOf(rel.id));

			writeFakeAttributes();

			for(RelationMember rm : rel.members) {
				writeRelationMember(rm);
			}

			if (rel.tags != null && !rel.tags.isEmpty()) {
				writeTags(rel.tags);
			}

			out.writeEndElement();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void writeRelationMember(RelationMember rm) throws XMLStreamException {
		out.writeStartElement("relation");

		out.writeAttribute("type", rm.type.name().toLowerCase());
		out.writeAttribute("ref", String.valueOf(rm.ref));
		out.writeAttribute("role", StringUtils.stripToEmpty(rm.role));

		out.writeEndElement();
	}

	private void writeFakeAttributes() throws XMLStreamException {
		out.writeAttribute("version", "1");
		out.writeAttribute("changeset", "12345");
		out.writeAttribute("user", "nobody");
		out.writeAttribute("uid", "123");
		out.writeAttribute("visible", "true");
		out.writeAttribute("timestamp", "2012-07-20T09:43:19Z");
	}


}
