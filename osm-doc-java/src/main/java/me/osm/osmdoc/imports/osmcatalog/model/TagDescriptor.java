package me.osm.osmdoc.imports.osmcatalog.model;

public class TagDescriptor {
	
	/**
	 * Key for translation
	 * */
	private String id;
	
	/**
	 * Ключ тэга в осм
	 * */
	private String osmTagName;

	/**
	 * Ключ для перевода значений
	 * */
	private String clazz;
	
	/**
	 * Тип значения <br>
	 * <ul>
	 * <li>translate - полностью переводимый набор значений (enum)
	 * <li>period - периоды времени (opening_hours) 
	 * <li>number - количественное значение (lanes); 
	 * <li>namelang - имена, имеющие переводы на языки (name:ru);
	 * </ul>
	 * */
	private String type;
	
	private boolean multyValue = false;
	
	private MoreTagsOwner owner = null;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getOsmTagName() {
		return osmTagName;
	}

	public void setOsmTagName(String osmTagName) {
		this.osmTagName = osmTagName;
	}

	public String getClazz() {
		return clazz;
	}

	public void setClazz(String clazz) {
		this.clazz = clazz;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public boolean isMultyValue() {
		return multyValue;
	}

	public void setMultyValue(boolean multyValue) {
		this.multyValue = multyValue;
	}

	public MoreTagsOwner getOwner() {
		return owner;
	}

	public void setOwner(MoreTagsOwner owner) {
		this.owner = owner;
	}
	
}
