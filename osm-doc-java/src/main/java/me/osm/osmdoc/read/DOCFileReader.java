package me.osm.osmdoc.read;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

import me.osm.osmdoc.model.DocPart;
import me.osm.osmdoc.model.Feature;
import me.osm.osmdoc.model.Hierarchy;
import me.osm.osmdoc.model.Trait;

import org.apache.commons.io.IOUtils;

public class DOCFileReader extends AbstractReader {
	
	private DocPart doc;

	private Map<String, Feature> featureByName = new HashMap<String, Feature>();

	private Map<String, Hierarchy> hierarchy2Name;

	private Map<String, Trait> traitByName;
	
	public DOCFileReader(String osmDocXML) {
		try {
			
			InputStream is = null;
			if("jar".equals(osmDocXML)) {
				is = DOCFileReader.class.getResourceAsStream("/osm-doc.xml");
			}
			else {
				is = new FileInputStream(new File(osmDocXML));
			}
			
			JAXBContext jaxbContext = JAXBContext.newInstance("me.osm.osmdoc.model", 
					me.osm.osmdoc.model.ObjectFactory.class.getClassLoader());
			
			Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
			
			byte[] encoded = IOUtils.toByteArray(is);
			String source = new String(encoded, Charset.forName("UTF8"));
			
			doc = (DocPart) unmarshaller.unmarshal(new StringReader(source));
			
			for(Feature f : doc.getFeature()) {
				featureByName.put(f.getName(), f);
			}
			
			hierarchy2Name = new HashMap<String, Hierarchy>();
			for(Hierarchy h : doc.getHierarchy()) {
				hierarchy2Name.put(h.getName(), h);
			}

			traitByName = new HashMap<String, Trait>();
			for(Trait t : doc.getTrait()) {
				traitByName.put(t.getName(), t);
			}
			
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/* (non-Javadoc)
	 * @see me.osm.osmdoc.read.DOCReader#getFeatures()
	 */
	@Override
	public List<Feature> getFeatures() {
		return new ArrayList<Feature>(doc.getFeature());
	}
	
	public DocPart getDoc() {
		return doc;
	}

	/* (non-Javadoc)
	 * @see me.osm.osmdoc.read.DOCReader#getHierarcyBranch(java.lang.String, java.lang.String)
	 */
	@Override
	public Collection<? extends Feature> getHierarcyBranch(
			String hierarchyName, String branch) {
		
		Set<String> excluded = new HashSet<String>();
		
		Hierarchy hierarchy = getHierarchy(hierarchyName);
		
		return getHierarcyBranch(branch, excluded, hierarchy, featureByName);
	}

	@Override
	public List<Hierarchy> listHierarchies() {
		return getDoc().getHierarchy();
	}

	@Override
	public Hierarchy getHierarchy(String name) {
		
		if(name==null && hierarchy2Name.size() == 1) {
			return hierarchy2Name.entrySet().iterator().next().getValue();
		}
		
		return hierarchy2Name.get(name);
	}

	@Override
	public Map<String, Trait> getTraits() {
		return traitByName;
	}

}
