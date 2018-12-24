package me.osm.osmdoc.imports.osmcatalog.model;

public class Tag {
	private String key;
	private String value;

	public Tag(String key, String value) {
		this.key = key;
		this.value = value;
	}
	
	public String getKey() {
		return key;
	}
	public void setKey(String key) {
		this.key = key;
	}
	public String getValue() {
		return value;
	}
	public void setValue(String value) {
		this.value = value;
	}
	
	@Override
	public String toString() {
		return getKey() + "=" + getValue();
	}
	
	@Override
	public boolean equals(Object obj) {
		return obj != null && toString().equals(obj.toString());
	}
	
	@Override
	public int hashCode() {
		return toString().hashCode();
	}
}
