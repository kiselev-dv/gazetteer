package me.osm.osmdoc.read;

import java.io.File;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
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

public class DOCFolderReader extends AbstractReader {
	
	private Map<String, Feature> featureByName;
	private Map<String, Hierarchy> hierarchies = new HashMap<String, Hierarchy>();
	private Map<String, Trait> traitByName = new HashMap<String, Trait>();

	public DOCFolderReader(String path) {
		
		featureByName = new HashMap<String, Feature>();
		
		File root = new File(path);
		iterateOverFiles(root);
	}

	private void iterateOverFiles(File root) {
		if(root.isFile()) {
			if(root.getName().endsWith(".xml")) {
				parse(root);
			}
		}
		else if(root.isDirectory()){
			for(File f : root.listFiles()) {
				iterateOverFiles(f);
			}
		}
	}

	private void parse(File root) {
		
		try {
			JAXBContext jaxbContext = JAXBContext.newInstance("me.osm.osmdoc.model", 
					me.osm.osmdoc.model.ObjectFactory.class.getClassLoader());
			
			Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
			
			byte[] encoded = Files.readAllBytes(root.toPath());
			
			String source = new String(encoded, Charset.forName("UTF8"));
			
			DocPart doc = (DocPart) unmarshaller.unmarshal(new StringReader(source));
			
			for(Feature f : doc.getFeature()) {
				featureByName.put(f.getName(), f);
			}
			
			for(Hierarchy h : doc.getHierarchy()) {
				hierarchies.put(h.getName(), h);
			}
			
			for(Trait t : doc.getTrait()) {
				traitByName.put(t.getName(), t);
			}
			
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to parse " + root.getName(), e);
		}
	}

	@Override
	public List<Feature> getFeatures() {
		return new ArrayList<Feature>(featureByName.values());
	}

	@Override
	public Collection<? extends Feature> getHierarcyBranch(
			String hierarchyName, String branch) {
		
		Set<String> excluded = new HashSet<String>();
		Hierarchy hierarchy = getHierarchy(hierarchyName);
		
		return getHierarcyBranch(branch, excluded, hierarchy, featureByName);
	}

	@Override
	public List<Hierarchy> listHierarchies() {
		return new ArrayList<Hierarchy>(hierarchies.values());
	}

	@Override
	public Hierarchy getHierarchy(String name) {
		
		if(name==null && hierarchies.size() == 1) {
			return hierarchies.entrySet().iterator().next().getValue();
		}
		
		return hierarchies.get(name);
	}

	@Override
	public Map<String, Trait> getTraits() {
		return traitByName;
	}
}
