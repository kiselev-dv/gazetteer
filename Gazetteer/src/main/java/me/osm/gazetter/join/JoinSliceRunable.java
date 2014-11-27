package me.osm.gazetter.join;

import static me.osm.gazetter.out.GazetteerSchemeConstants.GAZETTEER_SCHEME_MD5;
import static me.osm.gazetter.out.GazetteerSchemeConstants.GAZETTEER_SCHEME_TIMESTAMP;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import me.osm.gazetter.Options;
import me.osm.gazetter.addresses.AddrLevelsComparator;
import me.osm.gazetter.addresses.AddrLevelsSorting;
import me.osm.gazetter.addresses.AddressesParser;
import me.osm.gazetter.addresses.AddressesUtils;
import me.osm.gazetter.addresses.NamesMatcher;
import me.osm.gazetter.addresses.sorters.CityStreetHNComparator;
import me.osm.gazetter.addresses.sorters.HNStreetCityComparator;
import me.osm.gazetter.addresses.sorters.StreetHNCityComparator;
import me.osm.gazetter.join.PoiAddrJoinBuilder.BestFitAddresses;
import me.osm.gazetter.join.out_handlers.JoinOutHandler;
import me.osm.gazetter.join.util.JoinFailuresHandler;
import me.osm.gazetter.join.util.MemorySupervizor;
import me.osm.gazetter.join.util.MemorySupervizor.InsufficientMemoryException;
import me.osm.gazetter.striper.FeatureTypes;
import me.osm.gazetter.striper.GeoJsonWriter;
import me.osm.gazetter.striper.JSONFeature;
import me.osm.gazetter.utils.FileUtils;
import me.osm.gazetter.utils.JSONHash;
import me.osm.gazetter.utils.FileUtils.LineHandler;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.index.SpatialIndex;
import com.vividsolutions.jts.index.quadtree.Quadtree;
import com.vividsolutions.jts.index.strtree.STRtree;
import com.vividsolutions.jts.operation.buffer.BufferOp;

public class JoinSliceRunable implements Runnable {
	
	private static final Logger log = LoggerFactory.getLogger(JoinSliceRunable.class);
	
	private static final int MB = 1024*1024;
	
	private boolean intern = false;
	
	private static final double STREET_BUFFER_DISTANCE = 1.0 / 111195.0 * 500;
	private static final double POI_BUFFER_DISTANCE = 1.0 / 111195.0 * 100;
	
	private File src;
	
	// data and indexes 
	private final List<JSONObject> addrPoints = new ArrayList<>();
	
	private SpatialIndex addrPointsIndex = new STRtree();
	private SpatialIndex streetsPointsIndex = new STRtree();
	private SpatialIndex placesPointsIndex = new STRtree();
	
	private final List<JSONObject> boundaries = new ArrayList<>();
	private final List<JSONObject> places = new ArrayList<>();
	private final List<JSONObject> placesVoronoi = new ArrayList<>();
	private final List<JSONObject> neighboursVoronoi = new ArrayList<>();
	private final List<JSONObject> streets = new ArrayList<>();
	private final List<JSONObject> junctions = new ArrayList<>();
	private final List<JSONObject> associatedStreets = new ArrayList<>();
	
	private List<JSONObject> poi2bdng = new ArrayList<>();
	private List<JSONObject> addr2bdng = new ArrayList<>();

	private List<JSONObject> pois = new ArrayList<>();
	private SpatialIndex poisIndex = new Quadtree();

	private AddrJointHandler handler;
	private List<JSONObject> common;
	
	private Map<JSONObject, List<JSONObject>> addr2streets; 
	private Map<JSONObject, List<JSONObject>> addr2bndries; 
	private Map<JSONObject, List<JSONObject>> place2bndries; 
	
	private Map<JSONObject, List<List<JSONObject>>> street2bndries; 
	
	private Map<JSONObject, JSONObject> addr2PlaceVoronoy; 
	private Map<JSONObject, JSONObject> addr2NeighbourVoronoy ; 
	
	private Map<Long, Set<JSONObject>> street2Junctions; 
	
	private Map<Long, JSONObject> poiPnt2Builng;
	private Map<Long, JSONObject> addrPnt2Builng;
	
	private Map<JSONObject, List<JSONObject>> poi2bndries;
	private Map<String, JSONObject> addrPnt2AsStreet; 

	// dependancies --------------------------------------------------------------------------------
	private final PoiAddrJoinBuilder poiAddrJoinBuilder = new PoiAddrJoinBuilder();
	private final AddressesParser addressesParser = Options.get().getAddressesParser();
	private final NamesMatcher namesMatcher = Options.get().getNamesMatcher();
	private final AddrLevelsComparator addrLevelComparator;
	
	// misc
	private static final GeometryFactory factory = new GeometryFactory();
		
	private AtomicInteger stripesCounter;

	private Set<String> necesaryBoundaries;
	//private PrintWriter printWriter = null;

	//private File outFile;

	private JoinFailuresHandler failureHandler;
	
	public JoinSliceRunable(AddrJointHandler handler, File src, 
			List<JSONObject> common, Set<String> filter, JoinExecutor joiner, 
			JoinFailuresHandler failureHandler) {
		
		this.failureHandler = failureHandler;
		this.src = src;
		this.handler = handler;
		this.common = common;
		this.necesaryBoundaries = filter;
		
		if(log.isTraceEnabled() && joiner != null) {
			this.stripesCounter = joiner.getStripesCounter();
		}
		
		AddrLevelsSorting sorting = Options.get().getSorting();
		if(AddrLevelsSorting.HN_STREET_CITY == sorting) {
			addrLevelComparator = new HNStreetCityComparator();
		}
		else if (AddrLevelsSorting.CITY_STREET_HN == sorting) {
			addrLevelComparator = new CityStreetHNComparator();
		}
		else {
			addrLevelComparator = new StreetHNCityComparator();
		}
		
	}
	
	private static final ByIdComparator BY_ID_COMPARATOR = new ByIdComparator();

	private static final class ByIdComparator implements Comparator<JSONObject>{
		@Override
		public int compare(JSONObject arg0, JSONObject arg1) {
			String id0 = arg0.optString("id");
			String id1 = arg1.optString("id");
			return id0.compareTo(id1);
		}
	} 
	

	@Override
	public void run() {
		
		Thread.currentThread().setName("join-" + this.src.getName());
		
		try {
			long total = new Date().getTime();
			
			MemorySupervizor.checkMemory();
			
			long s = new Date().getTime();
			readFeatures();
			
			s = debug("readFeatures", s);
			
			initializeMaps();
			
			s = debug("initializeMaps", s);
			
			for(JSONObject point : addrPoints) {
				JSONArray ca = point.getJSONObject(GeoJsonWriter.GEOMETRY).getJSONArray(GeoJsonWriter.COORDINATES);
				addrPointsIndex.insert(new Envelope(new Coordinate(ca.getDouble(0), ca.getDouble(1))), point);
			}
			
			s = debug("fill addrPointsIndex", s);

			Collections.sort(boundaries, BY_ID_COMPARATOR);
			
			s = debug("sort boundaries", s);
			
			for(JSONObject street : streets) {
				JSONArray ca = street.getJSONObject(GeoJsonWriter.GEOMETRY).getJSONArray(GeoJsonWriter.COORDINATES);
				for(int i =0; i < ca.length(); i++) {
					JSONArray p = ca.getJSONArray(i);
					Coordinate coordinate = new Coordinate(p.getDouble(0), p.getDouble(1));
					streetsPointsIndex.insert(new Envelope(coordinate), new Object[]{coordinate, street});
				}
			}
			
			s = debug("fill streetsPointsIndex", s);
			
			for(JSONObject point : pois) {
				JSONArray ca = point.getJSONObject(GeoJsonWriter.GEOMETRY).getJSONArray(GeoJsonWriter.COORDINATES);
				poisIndex.insert(new Envelope(new Coordinate(ca.getDouble(0), ca.getDouble(1))), point);
			}
			
			s = debug("fill poisIndex", s);

			mergePois();
			
			s = debug("mergePois", s);
			
			for(JSONObject point : places) {
				JSONArray ca = point.getJSONObject(GeoJsonWriter.GEOMETRY).getJSONArray(GeoJsonWriter.COORDINATES);
				placesPointsIndex.insert(new Envelope(new Coordinate(ca.getDouble(0), ca.getDouble(1))), point);
			}
			
			s = debug("fill placesPointsIndex", s);
			
			for(JSONObject obj : poi2bdng) {
				poiPnt2Builng.put(obj.getLong("nodeId"), obj);
			}
			
			s = debug("fill poiPnt2Builng", s);
			
			for(JSONObject obj : addr2bdng) {
				addrPnt2Builng.put(obj.getLong("nodeId"), obj);
			}
			
			MemorySupervizor.checkMemory();
			
			join();
			
			write();

			for(JoinOutHandler h : Options.get().getJoinOutHandlers()) {
				h.stripeDone(this.src.getName());
			}
			
			if(log.isTraceEnabled() && this.stripesCounter != null) {
				log.info("Done. {} left", this.stripesCounter.decrementAndGet());
			}
			
			log.trace("total " + DurationFormatUtils.formatDurationHMS(new Date().getTime() - total));
		}
		catch (InsufficientMemoryException e) {
			log.trace("Join delayed. File: {}.", this.src);
			if(failureHandler != null) {
				failureHandler.failed(this.src);
			}
		}
		catch (Throwable t) {
			log.error("Join failed. File: {}. Error: {}", this.src, t.getMessage());
			
			if(failureHandler != null) {
				failureHandler.failed(this.src);
			}
		}
		finally {
			clean();
		}
		
	}

	private void clean() {
		addrPoints.clear();
		addrPointsIndex = null;
		streetsPointsIndex = null;
		placesPointsIndex = null;
		
		boundaries.clear();
		places.clear();
		placesVoronoi.clear();
		neighboursVoronoi.clear();
		streets.clear();
		junctions.clear();
		associatedStreets.clear();
		
		poi2bdng = null;
		addr2bdng = null;

		pois = null;
		poisIndex = null;

		addr2streets = null; 
		addr2bndries = null; 
		place2bndries = null; 
		
		street2bndries = null; 
		
		addr2PlaceVoronoy = null; 
		addr2NeighbourVoronoy = null; 
		
		street2Junctions = null; 
		
		poiPnt2Builng = null;
		addrPnt2Builng = null;
		
		poi2bndries = null;
		addrPnt2AsStreet = null; 

	}

	private long debug(String msg, long s) {
		Runtime runtime = Runtime.getRuntime();
		long f = new Date().getTime();
		log.trace(msg + " d:" + DurationFormatUtils.formatDurationHMS(f - s) + " m:" + 
				((runtime.totalMemory() - runtime.freeMemory()) / MB + "mb"));
		
		return f;
	}

	@SuppressWarnings("unchecked")
	private void mergePois() {
		for(JSONObject poi : pois) {
			JSONObject meta = poi.getJSONObject(GeoJsonWriter.META);
			JSONObject fullGeometry = meta.optJSONObject("fullGeometry");
			if(fullGeometry != null && "Polygon".equals(fullGeometry.getString("type"))) {
				Polygon poly = GeoJsonWriter.getPolygonGeometry(
						fullGeometry.getJSONArray(GeoJsonWriter.COORDINATES));
				
				List<JSONObject> dubles = poisIndex.query(poly.getEnvelopeInternal());
				if(dubles.size() > 1) {
					//remove self
					dubles.remove(poi);
					
					filterMatchedPois(poi, dubles);
					JSONObject poiProperties = poi.getJSONObject(GeoJsonWriter.PROPERTIES);
					
					Iterator<JSONObject> iterator = dubles.iterator();
					while(iterator.hasNext()) {
						JSONObject matched = iterator.next();
						JSONArray coords = matched.getJSONObject(GeoJsonWriter.GEOMETRY).getJSONArray(GeoJsonWriter.COORDINATES);
						
						Point centroid = factory.createPoint(new Coordinate(coords.getDouble(0), coords.getDouble(1)));
						if(!poly.contains(centroid)) {
							iterator.remove();
							continue;
						}
						
						matched.put("action", "remove");
						String poiId = poi.getString("id");
						matched.put("actionDetailed", 
								"Remove merged with polygonal boundary poi point." + poiId);
						
						JSONObject matchedProperties = matched.getJSONObject(GeoJsonWriter.PROPERTIES);
						for(String key : (Set<String>)matchedProperties.keySet()) {
							if(!poiProperties.has(key)) {
								poiProperties.put(key, matchedProperties.get(key));
							}
						}
					}
					
					//move center to the poi point instead of centroid
					if(dubles.size() == 1) {
						JSONArray coords = dubles.get(0).getJSONObject(GeoJsonWriter.GEOMETRY).getJSONArray(GeoJsonWriter.COORDINATES);
						poi.getJSONObject(GeoJsonWriter.GEOMETRY).put(GeoJsonWriter.COORDINATES, coords);
					}
					
				}
			}
		}
	}

	private void filterMatchedPois(JSONObject poi,
			List<JSONObject> dubles) {
		
		String clazz = poi.getJSONArray("poiTypes").getString(0);
		
		Iterator<JSONObject> iterator = dubles.iterator();
		while (iterator.hasNext()) {
			
			JSONObject candidate = iterator.next();
			if(!clazz.equals(candidate.getJSONArray("poiTypes"))) {
				iterator.remove();
			}
		}
	}

	private void initializeMaps() {
		addr2streets = new HashMap<JSONObject, List<JSONObject>>(addrPoints.size()); 
		addr2bndries = new HashMap<JSONObject, List<JSONObject>>(addrPoints.size()); 
		street2bndries = new HashMap<JSONObject, List<List<JSONObject>>> (streets.size()); 
		place2bndries = new HashMap<JSONObject, List<JSONObject>>(places.size()); 
		poi2bndries = new HashMap<JSONObject, List<JSONObject>>(pois.size()); 
		addr2PlaceVoronoy = new HashMap<>(addrPoints.size()); 
		addr2NeighbourVoronoy = new HashMap<>(addrPoints.size()); 
		
		street2Junctions = new HashMap<Long, Set<JSONObject>>(junctions.size() * 2);
		
		poiPnt2Builng = new HashMap<>(poi2bdng.size());
		addrPnt2Builng = new HashMap<>(addr2bdng.size());

		addrPnt2AsStreet = new HashMap<>(associatedStreets.size() * 10);
	}

	private void readFeatures() throws IOException, InsufficientMemoryException {
		try {
			FileUtils.handleLines(src, new LineHandler() {
				
				private int counter = 0;
				
				@Override
				public void handle(String line) {
					String ftype = GeoJsonWriter.getFtype(line);
					counter++;
					
					if(counter % 10000 == 0) {
						try {
							MemorySupervizor.checkMemory();
						}
						catch (InsufficientMemoryException e) {
							throw new RuntimeException(e);
						}
					}
					
					switch(ftype) {
					
					//Not an error, two cases with same behaviour. 
					case FeatureTypes.ADMIN_BOUNDARY_FTYPE:
					case FeatureTypes.PLACE_BOUNDARY_FTYPE:
						boundaries.add(new JSONFeature(line, intern));
						break;
						
					case FeatureTypes.ADDR_POINT_FTYPE:
						addrPoints.add(new JSONFeature(line, intern));
						break;
						
					case FeatureTypes.NEIGHBOUR_DELONEY_FTYPE: 
						neighboursVoronoi.add(new JSONFeature(line, intern));
						break;
						
					case FeatureTypes.PLACE_DELONEY_FTYPE:
						placesVoronoi.add(new JSONFeature(line, intern));
						break;
						
					case FeatureTypes.HIGHWAY_FEATURE_TYPE:
						streets.add(new JSONFeature(line, intern));
						break; 
						
					case FeatureTypes.POI_FTYPE:
						pois.add(new JSONFeature(line, intern));
						break;
						
					case FeatureTypes.JUNCTION_FTYPE:
						junctions.add(new JSONFeature(line, intern));
						break;
						
					case FeatureTypes.POI_2_BUILDING:
						poi2bdng.add(new JSONFeature(line, intern));
						break;
						
					case FeatureTypes.ADDR_NODE_2_BUILDING:
						addr2bdng.add(new JSONFeature(line, intern));
						break;
						
					case FeatureTypes.PLACE_POINT_FTYPE:
						places.add(new JSONFeature(line, intern));
						break;
						
					case FeatureTypes.ASSOCIATED_STREET:
						associatedStreets.add(new JSONFeature(line, intern));
						break;
					}
				}
				
			});
		}
		catch (Exception e) {
			if(e.getCause() instanceof InsufficientMemoryException) {
				throw (InsufficientMemoryException)e.getCause();
			}
			else {
				throw e;
			} 
		}
	}
	
	private void join() throws InsufficientMemoryException {
		
		long s = new Date().getTime();
		
		Collections.sort(boundaries, new Comparator<JSONObject>() {

			@Override
			public int compare(JSONObject b1, JSONObject b2) {
				int lvl1 = getBlevel(b1);

				int lvl2 = getBlevel(b2);
				
				return Integer.compare(lvl1, lvl2);
			}
			
		});
		
		s = debug("sort boundaries", s);
		
		joinNeighbourPlaces(places, placesVoronoi);
		
		for(JSONObject boundary : boundaries) {
			Polygon polygon = GeoJsonWriter.getPolygonGeometry(boundary);
			
			many2ManyJoin(boundary, polygon, addr2bndries, addrPointsIndex);
			many2ManyJoin(boundary, polygon, place2bndries, placesPointsIndex);
			many2ManyJoin(boundary, polygon, poi2bndries, poisIndex);
			highwaysJoin(boundary, polygon, street2bndries, streetsPointsIndex);
			
		}
		
		MemorySupervizor.checkMemory();
		
		s = debug("join [addr2bndries, place2bndries, poi2bndries, street2bndries]", s);
		
		joinBoundaries2Streets();
		s = debug("write json streets to boundaries", s);
		
		joinStreets2Addresses();
		s = debug("joinStreets2Addresses", s);
		
		one2OneJoin(placesVoronoi, addr2PlaceVoronoy);
		s = debug("join addr2PlaceVoronoy", s);
		
		one2OneJoin(neighboursVoronoi, addr2NeighbourVoronoy);
		s = debug("join addr2NeighbourVoronoy", s);
		
		joinJunctionsWithStreets();
		s = debug("joinJunctionsWithStreets", s);
		
		joinBndg2Poi();
		s = debug("joinBndg2Poi", s);
		
		joinBndg2Addr();
		s = debug("joinBndg2Addr", s);

		fillAddr2AsStreet();
		s = debug("fillAddr2AsStreet", s);
		
		joinPoi2Addresses();
		s = debug("joinPoi2Addresses", s);
		
	}

	private void joinNeighbourPlaces(List<JSONObject> places,
			List<JSONObject> placesVoronoi) {

		Map<String, JSONObject> byId = new HashMap<String, JSONObject>(placesVoronoi.size());
		for(JSONObject vor : placesVoronoi) {
			String placeId = StringUtils.replace(vor.getString("id"), 
					FeatureTypes.PLACE_DELONEY_FTYPE, FeatureTypes.PLACE_POINT_FTYPE);
			byId.put(placeId, vor);
		}
		
		for(JSONObject obj : places) {
			JSONObject vor = byId.get(obj.getString("id"));
			if(vor != null) {
				obj.put("neighbourCities", vor.get("neighbourCities"));
			}
		}
	}

	private void joinBoundaries2Streets() {
		Map<Integer, Set<String>> streetsByBndrs = new HashMap<Integer, Set<String>>();
		for(Entry<JSONObject, List<List<JSONObject>>> entry : street2bndries.entrySet()) {
			if(streetsByBndrs.get(entry.getValue().size()) == null) {
				streetsByBndrs.put(entry.getValue().size(), new HashSet<String>());
			}
			streetsByBndrs.get(entry.getValue().size()).add(entry.getKey().getString("id"));
		}
		
		for(Entry<JSONObject, List<List<JSONObject>>> entry : street2bndries.entrySet()) {
			
			List<List<JSONObject>> boundaries = new ArrayList<>(entry.getValue());
			
			JSONArray bndriesJSON = new JSONArray();
			for(List<JSONObject> b : boundaries) {
				if(necesaryBoundaries.isEmpty() || checkNecesaryBoundaries(b)) {
					b.addAll(common);
					bndriesJSON.put(addressesParser.boundariesAsArray(entry.getKey(), b));
				}

			}

			if(bndriesJSON.length() > 0) {
				JSONObject street = entry.getKey();
				street.put("boundaries", bndriesJSON);
			}
		}
	}

	private boolean checkNecesaryBoundaries(List<JSONObject> joinedBoundaries) {
		for(JSONObject b : joinedBoundaries) {
			String osmId = StringUtils.split(b.getString("id"), '-')[2];
			if(necesaryBoundaries.contains(osmId)) {
				return true;
			}
		}
		return false;
	}

	private void fillAddr2AsStreet() {
		for(JSONObject association : associatedStreets) {
			JSONArray barray = association.getJSONArray("buildings");
			
			for(int i = 0; i < barray.length(); i++) {
				String key = barray.getString(i);
				addrPnt2AsStreet.put(key, association);
			}
		}
	}

	private void joinStreets2Addresses() {
		for (JSONObject strtJSON : street2bndries.keySet()) {
			LineString ls = GeoJsonWriter.getLineStringGeometry(
					strtJSON.getJSONObject("geometry").getJSONArray("coordinates"));
			
			@SuppressWarnings("deprecation")
			Geometry buffer = ls.buffer(STREET_BUFFER_DISTANCE, 2, BufferOp.CAP_ROUND);
			if(buffer instanceof Polygon) {
				many2ManyJoin(strtJSON, (Polygon) buffer, addr2streets, addrPointsIndex);
			}
			else if(buffer instanceof MultiPolygon) {
				for(int i = 0; i < buffer.getNumGeometries(); i++) {
					Polygon p = (Polygon) buffer.getGeometryN(i);
					if(p.isValid()) {
						many2ManyJoin(strtJSON, p, addr2streets, addrPointsIndex);
					}
				}
			}
		}
	}

	private void joinPoi2Addresses() {
		Iterator<JSONObject> iterator = pois.iterator();
		
		while(iterator.hasNext()) {
			JSONObject poi = iterator.next();
			
			JSONArray coords = poi.getJSONObject(GeoJsonWriter.GEOMETRY).getJSONArray(GeoJsonWriter.COORDINATES);
			double lon = coords.getDouble(0);
			double lat = coords.getDouble(1);
			
			Coordinate p1 = new Coordinate(lon - POI_BUFFER_DISTANCE, lat - POI_BUFFER_DISTANCE);
			Coordinate p2 = new Coordinate(lon + POI_BUFFER_DISTANCE, lat + POI_BUFFER_DISTANCE);
			
			@SuppressWarnings("unchecked")
			List<JSONObject> addrPnts = addrPointsIndex.query(new Envelope(p1, p2));
			
			addrPnts = copyWithAddrJoin(addrPnts);
			
			if(addrPnts != null && !addrPnts.isEmpty()) {
				BestFitAddresses join = poiAddrJoinBuilder.join(poi, addrPnts);
				poi.put("joinedAddresses", join.asJSON());
				poi.put("nearbyAddresses", getIds(addrPnts));
			}
			
			List<JSONObject> boundaries = nullSafeList(poi2bndries.get(poi));
			
			if(necesaryBoundaries.isEmpty() || checkNecesaryBoundaries(boundaries)) {
				boundaries.addAll(common);
				poi.put("boundaries", addressesParser.boundariesAsArray(poi, boundaries));
			}
			
			GeoJsonWriter.addTimestamp(poi);
			GeoJsonWriter.addMD5(poi);
			
			handleOut(poi);
			
			iterator.remove();
		}
		
		poi2bndries = null;
		poisIndex = null;
	}

	
	private void handleOut(JSONObject poi) {
		for(JoinOutHandler handler : Options.get().getJoinOutHandlers()) {
			handler.handle(poi, this.src.getName());
		}
	}

	private Collection<String> getIds(List<JSONObject> addrPnts) {
		List<String> result = new ArrayList<String>();
		
		for(JSONObject obj : addrPnts) {
			result.add(obj.getString("id"));
		}
		
		return result;
	}

	private List<JSONObject> copyWithAddrJoin(List<JSONObject> addrPnts) {
		
		List<JSONObject> result = new ArrayList<JSONObject>();
		
		for(JSONObject adr : addrPnts) {
			List<JSONObject> boundaries = nullSafeList(addr2bndries.get(adr));
			if(necesaryBoundaries.isEmpty() || checkNecesaryBoundaries(boundaries)) {
				boundaries.addAll(common);
				
				String assStreetAddrKey = StringUtils.split(adr.getString("id"), '-')[2];
				
				JSONObject r = new JSONFeature(adr);
				handler.handle(
						r, 
						boundaries, 
						addr2streets.get(adr),
						addr2PlaceVoronoy.get(adr), 
						addr2NeighbourVoronoy.get(adr), 
						addrPnt2AsStreet.get(assStreetAddrKey));
				
				result.add(r);
			}
			
		}
		
		return result;
	}

	private List<JSONObject> nullSafeList(List<JSONObject> list) {
		if(list != null) {
			return list;
		}
		
		return new ArrayList<JSONObject>();
	}

	private void joinBndg2Addr() {
		for(JSONObject obj : addr2bdng) {
			addrPnt2Builng.put(obj.getLong("nodeId"), obj);
		}

		for(JSONObject addr : addrPoints) {
			JSONObject meta = addr.getJSONObject(GeoJsonWriter.META);
			if ("node".equals(meta.getString("type"))) {
				JSONObject bndbg = poiPnt2Builng.get(meta.getLong("id"));
				putLinkedBuilding(addr, bndbg);
			}
		}
	}

	private void putLinkedBuilding(JSONObject addr, JSONObject bndbg) {
		if(bndbg != null) {
			JSONObject jb = new JSONObject();
			jb.put("id", bndbg.getJSONObject(GeoJsonWriter.META).getLong("id"));
			jb.put(GeoJsonWriter.PROPERTIES, bndbg.getJSONObject(GeoJsonWriter.PROPERTIES));
			addr.put("bndgWay", jb);
		}
	}

	private void joinBndg2Poi() {
		for(JSONObject obj : poi2bdng) {
			poiPnt2Builng.put(obj.getLong("nodeId"), obj);
		}

		for(JSONObject poi : pois) {
			JSONObject meta = poi.getJSONObject(GeoJsonWriter.META);
			if ("node".equals(meta.getString("type"))) {
				JSONObject bndbg = poiPnt2Builng.get(meta.getLong("id"));
				putLinkedBuilding(poi, bndbg);
			}
		}
	}

	protected void write() {
		
		long s = new Date().getTime();
		
		try {

			Iterator<JSONObject> iterator = addrPoints.iterator();
			while(iterator.hasNext()) {
				JSONObject adr = iterator.next();
				
				List<JSONObject> boundaries = nullSafeList(addr2bndries.get(adr));
				if(necesaryBoundaries.isEmpty() || checkNecesaryBoundaries(boundaries)) {
					boundaries.addAll(common);
					
					String assStreetAddrKey = StringUtils.split(adr.getString("id"), '-')[2];
					
					handler.handle(
							adr, 
							boundaries, 
							addr2streets.get(adr),
							addr2PlaceVoronoy.get(adr), 
							addr2NeighbourVoronoy.get(adr), 
							addrPnt2AsStreet.get(assStreetAddrKey));
					
				}
				
				
				GeoJsonWriter.addTimestamp(adr);
				GeoJsonWriter.addMD5(adr);
				
				handleOut(adr);
				
				iterator.remove();
			}
			addr2bdng = null;
			addr2bndries = null;
			addr2NeighbourVoronoy = null;
			addr2PlaceVoronoy = null;
			addr2streets = null;
			
			s = debug("write out addrPoints", s);
			
			for(JSONObject street : street2bndries.keySet()) {
				if(street.has("boundaries")) {
					GeoJsonWriter.addTimestamp(street);
					GeoJsonWriter.addMD5(street);
					
					handleOut(street);
				}
			}
			
			s = debug("write out street2bndries", s);
			
			for(JSONObject jun : junctions) {
				GeoJsonWriter.addTimestamp(jun);
				GeoJsonWriter.addMD5(jun);
				handleOut(jun);
			}
			
			s = debug("write out junctions", s);
			
			for(Entry<JSONObject, List<JSONObject>> entry : place2bndries.entrySet()) {
				List<JSONObject> boundaries = new ArrayList<>(entry.getValue());
				if(necesaryBoundaries.isEmpty() || checkNecesaryBoundaries(boundaries)) {
					boundaries.addAll(common);
					JSONObject place = entry.getKey();
					
					String name = place.getJSONObject(GeoJsonWriter.PROPERTIES).optString("name");
					for(JSONObject b : boundaries) {
						if(namesMatcher.isPlaceNameMatch(name, AddressesUtils.filterNameTags(b))) {
							place.put("matchedBoundary", b);
							break;
						}
					}
	
					place.put("boundaries", addressesParser.boundariesAsArray(entry.getKey(), boundaries));
					GeoJsonWriter.addTimestamp(place);
					GeoJsonWriter.addMD5(place);
					
					handleOut(place);
				}
			}
			
			s = debug("write out place2bndries", s);
			
			for(JSONObject boundary : boundaries) {
				GeoJsonWriter.addTimestamp(boundary);
				GeoJsonWriter.addMD5(boundary);
				handleOut(boundary);
			}
			
			s = debug("write out boundaries", s);

			for(JSONObject obj : placesVoronoi) {
				GeoJsonWriter.addTimestamp(obj);
				GeoJsonWriter.addMD5(obj);
				handleOut(obj);
			}
			
			s = debug("write out placesVoronoi", s);

			for(JSONObject obj : neighboursVoronoi) {
				GeoJsonWriter.addTimestamp(obj);
				GeoJsonWriter.addMD5(obj);
				handleOut(obj);
			}
			
			s = debug("write out neighboursVoronoi", s);
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to write joined stripe. File: " + this.src, e);
		}
	}

	private void joinJunctionsWithStreets() {
		for(JSONObject junction : junctions) {
			JSONArray hwIds = junction.optJSONArray("ways");
			
			for(int i = 0; i < hwIds.length(); i++) {
				Long hw = hwIds.getLong(i);
				if(street2Junctions.get(hw) == null) {
					street2Junctions.put(hw, new HashSet<JSONObject>());
				}
				street2Junctions.get(hw).add(junction);
			}
		}
		
		for(JSONObject way : streets) {
			Set<JSONObject> junctionsSet = street2Junctions.get(way.getJSONObject(GeoJsonWriter.META).getLong("id"));
			if(junctionsSet != null) {
				for(JSONObject j : junctionsSet) {
					JSONArray waysRefers = j.optJSONArray("waysRefers");
					if(waysRefers == null) {
						waysRefers = new JSONArray();
						j.put("waysRefers", waysRefers);
					}
					
					waysRefers.put(JSONFeature.asRefer(way));
				}
			}
		}
	}

	private void many2ManyJoin(JSONObject object, Polygon polyg, Map<JSONObject, List<JSONObject>> result, SpatialIndex index) {
		Envelope polygonEnvelop = polyg.getEnvelopeInternal();
		for (Object entry : index.query(polygonEnvelop)) {
			
			JSONArray pntg = ((JSONObject)entry).getJSONObject(GeoJsonWriter.GEOMETRY).getJSONArray(GeoJsonWriter.COORDINATES);
			Coordinate pnt = new Coordinate(pntg.getDouble(0), pntg.getDouble(1));;
			JSONObject obj = (JSONObject) entry;
			
			if(polyg.intersects(factory.createPoint(pnt))) {
				if(result.get(obj) == null) {
					result.put(obj, new ArrayList<JSONObject>());
				}
				
				result.get(obj).add(object);
			}
		}
	}

	private void highwaysJoin(JSONObject newBoundary, Polygon polyg, Map<JSONObject, List<List<JSONObject>>> result, SpatialIndex index) {
		
		Envelope polygonEnvelop = polyg.getEnvelopeInternal();
		
		Set<JSONObject> uniqueHW = new HashSet<JSONObject>();
		for (Object entry : index.query(polygonEnvelop)) {
			Coordinate pnt = (Coordinate) ((Object[])entry)[0];
			JSONObject highway = (JSONObject) ((Object[])entry)[1];
			if(polyg.contains(factory.createPoint(pnt))) {
				uniqueHW.add(highway);
			}
		}
		
		for (JSONObject highway: uniqueHW ) { 
			int bLevel = getBlevel(newBoundary);
			
			if(result.get(highway) == null) {
				result.put(highway, new ArrayList<List<JSONObject>>());
			}
			
			List<List<JSONObject>> listList = result.get(highway);
			
			//start new boundaries row
			if(listList.size() == 0) {
				ArrayList<JSONObject> boundariesRow = new ArrayList<JSONObject>();
				boundariesRow.add(newBoundary);
				listList.add(boundariesRow);
			}
			
			//1 row (as usual)
			else if(listList.size() == 1) {
				List<JSONObject> row = listList.get(0);
				JSONObject last = row.get(row.size() - 1);
				int oldBLevel = getBlevel(last);
				
				//we have two or more boundaries with same level
				//so we need to split addr row
				if(oldBLevel > 0 && oldBLevel == bLevel) {
					List<JSONObject> newRow = new ArrayList<>(row);
					newRow.remove(newRow.size() - 1);
					newRow.add(newBoundary);
					listList.add(newRow);
				}
				else {
					row.add(newBoundary);
				}
			}
			
			//already splitted up
			else {
				//find our
				for (List<JSONObject> row : listList) {
					JSONObject last = row.get(row.size() - 1);
					int oldBLevel = getBlevel(last);
					
					//add to new row
					if(oldBLevel > 0 && oldBLevel == bLevel) {
						List<JSONObject> newRow = new ArrayList<>(row);
						newRow.remove(newRow.size() - 1);
						newRow.add(newBoundary);
						listList.add(newRow);
						break;
					}
					
					//add to exists row (with coordinates check)
					else {
						JSONArray coords = last.getJSONObject(GeoJsonWriter.GEOMETRY)
								.getJSONArray(GeoJsonWriter.COORDINATES);
						
						Point centroid = GeoJsonWriter.getPolygonGeometry(coords).getCentroid();
						
						if(polyg.contains(centroid)) {
							row.add(newBoundary);
						}
					}
				}
			}
		}
	}

	private int getBlevel(JSONObject newBoundary) {
		int bLevel = 0;
		String addrLevel = addressesParser.getAddrLevel(newBoundary);
		if(addrLevel != null) {
			bLevel = addrLevelComparator.getLVLSize(addrLevel);
		}
		return bLevel;
	}

	@SuppressWarnings("unchecked")
	private void one2OneJoin(List<JSONObject> polygons, Map<JSONObject, JSONObject> result) {
		for(JSONObject placeV : polygons) {
			Polygon polyg = GeoJsonWriter.getPolygonGeometry(placeV);
			Envelope polygonEnvelop = polyg.getEnvelopeInternal();
			for (JSONObject pnt : (List<JSONObject>)addrPointsIndex.query(polygonEnvelop)) {
				JSONArray pntg = pnt.getJSONObject(GeoJsonWriter.GEOMETRY).getJSONArray(GeoJsonWriter.COORDINATES);
				if(polyg.contains(factory.createPoint(new Coordinate(pntg.getDouble(0), pntg.getDouble(1))))){
					result.put(pnt, placeV);
				}
			}
		}
	}

}
