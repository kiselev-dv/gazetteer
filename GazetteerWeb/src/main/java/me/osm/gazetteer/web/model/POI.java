package me.osm.gazetteer.web.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public interface POI {
	
	@JsonProperty("poi_class")
	public String getPoiClass();
	public void setPoiClass(String poiClass);
	
	@JsonProperty("poi_class_names")
	public List<String> getPoiClassNames();
	public void setPoiClassNames(List<String> poiClassNames);
	
	@JsonProperty("operator")
	public String getOperator();
	public void setOperator(String operator);
	
	@JsonProperty("brand")
	public String getBrand();
	public void setBrand(String brand);
	
	@JsonProperty("opening_hours")
	public String getOpeningHours();
	public void setOpeningHours(String openingHours);
	
	@JsonProperty("phone")
	public String getPhone();
	public void setPhone(String phone);
	
	@JsonProperty("email")
	public String getEmail();
	public void setEmail(String email);
	
	@JsonProperty("website")
	public String getWebsite();
	public void setWebsite(String website);
	
}
