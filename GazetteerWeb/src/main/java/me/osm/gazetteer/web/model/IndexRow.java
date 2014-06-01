package me.osm.gazetteer.web.model;

import java.util.Date;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ 
	"id", 
	"feature_id", 
	"type", 
	"addr_level", 
	"timestamp", 
	"name", 
	"alt_names", 
	"nearby_streets", 
	"nearby_places", 
	"address", 
	"alt_addresses", 
	"scheme", 
	"admin0_name", 
	"admin0_alternate_names", 
	"admin1_name", 
	"admin1_alternate_names", 
	"admin2_name", 
	"admin2_alternate_names", 
	"local_admin_name", 
	"local_admin_alternate_names", 
	"locality_name", 
	"locality_alternate_names", 
	"neighborhood_name", 
	"neighborhood_alternate_names", 
	"street_name", 
	"street_alternate_names", 
	"housenumber", 
	"refs", 
	"tags", 
	"poi_class", 
	"poi_class_names", 
	"operator", 
	"brand", 
	"opening_hours", 
	"phone", 
	"email", 
	"website", 
	"center_point", 
	"full_geometry" 
})
public class IndexRow implements Ref, Address, POI {
	
	// ref
	private String id;
	private String name;
	private List<String> altNames;
	
	@Override
	public String getId() {
		return id;
	}
	@Override
	public void setId(String id) {
		this.id = id;
	}
	@Override
	public String getName() {
		return name;
	}
	@Override
	public void setName(String name) {
		this.name = name;
	}
	@Override
	public List<String> getAltNames() {
		return altNames;
	}
	@Override
	public void setAltNames(List<String> altNames) {
		this.altNames = altNames;
	}
	// end ref
	
	@JsonProperty("feature_id")
	private String featureId;
	
	@JsonProperty("type")
	private FTYPE type;

	@JsonProperty("addr_level")
	private String addrLevel;

	@JsonProperty("timestamp")
	private Date timestamp;

	@JsonProperty("nearby_streets")
	private List<StreetRef> nearbyStreets;

	@JsonProperty("nearby_places")
	private List<PlaceRef> nearbyPlaces;
	
	
	//--------- ADDRESS --------------
	private String address;

	private List<String> altAddresses;
	
	private String scheme;

	private String admin0Name;
	private List<String> admin0AltNames;

	private String admin1Name;
	private List<String> admin1AltNames;

	private String admin2Name;
	private List<String> admin2AltNames;

	private String localAdminName;
	private List<String> localAdminAltNames;

	private String localityName;
	private List<String> localityAltNames;

	private String neighborhoodName;
	private List<String> neighborhoodAltNames;

	private String streetName;
	private List<String> streetAltNames;

	private String housenumber;
	private Map<String, String> refs; 

	@Override
	public String getAddress() {
		return address;
	}
	@Override
	public void setAddress(String address) {
		this.address = address;
	}
	@Override
	public List<String> getAltAddresses() {
		return altAddresses;
	}
	@Override
	public void setAltAddresses(List<String> altAddresses) {
		this.altAddresses = altAddresses;
	}
	@Override
	public String getScheme() {
		return scheme;
	}
	@Override
	public void setScheme(String scheme) {
		this.scheme = scheme;
	}
	@Override
	public String getAdmin0Name() {
		return admin0Name;
	}
	@Override
	public void setAdmin0Name(String admin0Name) {
		this.admin0Name = admin0Name;
	}
	@Override
	public List<String> getAdmin0AltNames() {
		return admin0AltNames;
	}
	@Override
	public void setAdmin0AltNames(List<String> admin0AltNames) {
		this.admin0AltNames = admin0AltNames;
	}
	@Override
	public String getAdmin1Name() {
		return admin1Name;
	}
	@Override
	public void setAdmin1Name(String admin1Name) {
		this.admin1Name = admin1Name;
	}
	@Override
	public List<String> getAdmin1AltNames() {
		return admin1AltNames;
	}
	@Override
	public void setAdmin1AltNames(List<String> admin1AltNames) {
		this.admin1AltNames = admin1AltNames;
	}
	@Override
	public String getAdmin2Name() {
		return admin2Name;
	}
	@Override
	public void setAdmin2Name(String admin2Name) {
		this.admin2Name = admin2Name;
	}
	@Override
	public List<String> getAdmin2AltNames() {
		return admin2AltNames;
	}
	@Override
	public void setAdmin2AltNames(List<String> admin2AltNames) {
		this.admin2AltNames = admin2AltNames;
	}
	@Override
	public String getLocalAdminName() {
		return localAdminName;
	}
	@Override
	public void setLocalAdminName(String localAdminName) {
		this.localAdminName = localAdminName;
	}
	@Override
	public List<String> getLocalAdminAltNames() {
		return localAdminAltNames;
	}
	@Override
	public void setLocalAdminAltNames(List<String> localAdminAltNames) {
		this.localAdminAltNames = localAdminAltNames;
	}
	@Override
	public String getLocalityName() {
		return localityName;
	}
	@Override
	public void setLocalityName(String localityName) {
		this.localityName = localityName;
	}
	@Override
	public List<String> getLocalityAltNames() {
		return localityAltNames;
	}
	@Override
	public void setLocalityAltNames(List<String> localityAltNames) {
		this.localityAltNames = localityAltNames;
	}
	@Override
	public String getNeighborhoodName() {
		return neighborhoodName;
	}
	@Override
	public void setNeighborhoodName(String neighborhoodName) {
		this.neighborhoodName = neighborhoodName;
	}
	@Override
	public List<String> getNeighborhoodAltNames() {
		return neighborhoodAltNames;
	}
	@Override
	public void setNeighborhoodAltNames(List<String> neighborhoodAltNames) {
		this.neighborhoodAltNames = neighborhoodAltNames;
	}
	@Override
	public String getStreetName() {
		return streetName;
	}
	@Override
	public void setStreetName(String streetName) {
		this.streetName = streetName;
	}
	@Override
	public List<String> getStreetAltNames() {
		return streetAltNames;
	}
	@Override
	public void setStreetAltNames(List<String> streetAltNames) {
		this.streetAltNames = streetAltNames;
	}
	@Override
	public String getHousenumber() {
		return housenumber;
	}
	@Override
	public void setHousenumber(String housenumber) {
		this.housenumber = housenumber;
	}
	@Override
	public Map<String, String> getRefs() {
		return refs;
	}
	@Override
	public void setRefs(Map<String, String> refs) {
		this.refs = refs;
	}
	
	// end ADDRESS

	//-------- TAGS -----------------
	@JsonProperty("tags")
	private Map<String, String> tags;
	
	//-------------- POI ------------
	private String poiClass;
	private List<String> poiClassNames;

	private String operator;
	private String brand;
	private String openingHours;
	private String phone;
	private String email;
	private String website;
	
	@Override
	public String getPoiClass() {
		return poiClass;
	}
	@Override
	public void setPoiClass(String poiClass) {
		this.poiClass = poiClass;
	}
	@Override
	public List<String> getPoiClassNames() {
		return poiClassNames;
	}
	@Override
	public void setPoiClassNames(List<String> poiClassNames) {
		this.poiClassNames = poiClassNames;
	}
	@Override
	public String getOperator() {
		return operator;
	}
	@Override
	public void setOperator(String operator) {
		this.operator = operator;
	}
	@Override
	public String getBrand() {
		return brand;
	}
	@Override
	public void setBrand(String brand) {
		this.brand = brand;
	}
	@Override
	public String getOpeningHours() {
		return openingHours;
	}
	@Override
	public void setOpeningHours(String openingHours) {
		this.openingHours = openingHours;
	}
	@Override
	public String getPhone() {
		return phone;
	}
	@Override
	public void setPhone(String phone) {
		this.phone = phone;
	}
	@Override
	public String getEmail() {
		return email;
	}
	@Override
	public void setEmail(String email) {
		this.email = email;
	}
	@Override
	public String getWebsite() {
		return website;
	}
	@Override
	public void setWebsite(String website) {
		this.website = website;
	}
	
	//------------ Geometry -----------
	
	@JsonProperty("center_point")
	private RawJSON centroid;
	
	@JsonProperty("full_geometry")
	private RawJSON fullGeometry;
	
	//--------Getters & Setters -------	

	public String getFeatureId() {
		return featureId;
	}
	public void setFeatureId(String featureId) {
		this.featureId = featureId;
	}
	public FTYPE getType() {
		return type;
	}
	public void setType(FTYPE type) {
		this.type = type;
	}
	public String getAddrLevel() {
		return addrLevel;
	}
	public void setAddrLevel(String addrLevel) {
		this.addrLevel = addrLevel;
	}
	public Date getTimestamp() {
		return timestamp;
	}
	public void setTimestamp(Date timestamp) {
		this.timestamp = timestamp;
	}
	public List<StreetRef> getNearbyStreets() {
		return nearbyStreets;
	}
	public void setNearbyStreets(List<StreetRef> nearbyStreets) {
		this.nearbyStreets = nearbyStreets;
	}
	public List<PlaceRef> getNearbyPlaces() {
		return nearbyPlaces;
	}
	public void setNearbyPlaces(List<PlaceRef> nearbyPlaces) {
		this.nearbyPlaces = nearbyPlaces;
	}
	
	public Map<String, String> getTags() {
		return tags;
	}
	public void setTags(Map<String, String> tags) {
		this.tags = tags;
	}
	
	public RawJSON getCentroid() {
		return centroid;
	}
	public void setCentroid(RawJSON centroid) {
		this.centroid = centroid;
	}
	public RawJSON getFullGeometry() {
		return fullGeometry;
	}
	public void setFullGeometry(RawJSON fullGeometry) {
		this.fullGeometry = fullGeometry;
	}
	
}
