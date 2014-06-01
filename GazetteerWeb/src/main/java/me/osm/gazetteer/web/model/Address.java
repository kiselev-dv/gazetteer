package me.osm.gazetteer.web.model;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public interface Address {
	
	@JsonProperty("address")
	public String getAddress();
	public void setAddress(String address);
	
	@JsonProperty("alt_addresses")
	public List<String> getAltAddresses();
	public void setAltAddresses(List<String> altAddresses);
	
	@JsonProperty("scheme")
	public String getScheme();
	public void setScheme(String scheme);
	
	@JsonProperty("admin0_name")
	public String getAdmin0Name();
	public void setAdmin0Name(String admin0Name);
	
	@JsonProperty("admin0_alternate_names")
	public List<String> getAdmin0AltNames();
	public void setAdmin0AltNames(List<String> admin0AltNames);
	
	@JsonProperty("admin1_name")
	public String getAdmin1Name();
	public void setAdmin1Name(String admin1Name);
	
	@JsonProperty("admin1_alternate_names")
	public List<String> getAdmin1AltNames();
	public void setAdmin1AltNames(List<String> admin1AltNames);
	
	@JsonProperty("admin2_name")
	public String getAdmin2Name();
	public void setAdmin2Name(String admin2Name);
	
	@JsonProperty("admin2_alternate_names")
	public List<String> getAdmin2AltNames();
	public void setAdmin2AltNames(List<String> admin2AltNames);
	
	@JsonProperty("local_admin_name")
	public String getLocalAdminName();
	public void setLocalAdminName(String localAdminName);
	
	@JsonProperty("local_admin_alternate_names")
	public List<String> getLocalAdminAltNames();
	public void setLocalAdminAltNames(List<String> localAdminAltNames);
	
	@JsonProperty("locality_name")
	public String getLocalityName();
	public void setLocalityName(String localityName);
	
	@JsonProperty("locality_alternate_names")
	public List<String> getLocalityAltNames();
	public void setLocalityAltNames(List<String> localityAltNames);
	
	@JsonProperty("neighborhood_name")
	public String getNeighborhoodName();
	public void setNeighborhoodName(String neighborhoodName);
	
	@JsonProperty("neighborhood_alternate_names")
	public List<String> getNeighborhoodAltNames();
	public void setNeighborhoodAltNames(List<String> neighborhoodAltNames);
	
	@JsonProperty("street_name")
	public String getStreetName();
	public void setStreetName(String streetName);
	
	@JsonProperty("street_alternate_names")
	public List<String> getStreetAltNames();
	public void setStreetAltNames(List<String> streetAltNames);
	
	@JsonProperty("housenumber")
	public String getHousenumber();
	public void setHousenumber(String housenumber);
	
	@JsonProperty("refs")
	public Map<String, String> getRefs();
	public void setRefs(Map<String, String> refs);
}
