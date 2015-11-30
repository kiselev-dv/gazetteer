package me.osm.gazetteer.web.api.search;

import java.io.File;
import java.io.FileReader;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.Properties;

public class Weights {

	private static final String defLocation = "config/search.weights";
	
	/**
	 * Marker for managed and autoloaded fields
	 * */
	@Target(ElementType.FIELD)
	@Retention(RetentionPolicy.RUNTIME)
	private static @interface Auto {}

	
	/**
	 * We trying to boost some housenumber variants. 
	 * They are boosted accordingly to their position in variants list.
	 * 
	 * This is the step which will be added to every next variant.
	 * */
	@Auto
	public float hnVariansStep = 20;

	/**
	 * Boost for housenumbers among common search field.
	 * */
	@Auto
	public float hnsInSearch = 10;

	/**
	 * If there are more then one numbered term in query
	 * boost numbers among street name.
	 * 
	 * Should be greater than housenumbers matches 
	 * */
	@Auto
	public int numbersInStreeMul = 50;

	/**
	 * Boost for numbers subquery
	 * */
	@Auto
	public float numberQBoost = 10;

	/**
	 * Boost for optional terms
	 * */
	@Auto
	public float optionalTermBoost = 5;

	/**
	 * Boost for solo number in query among housenumber 
	 * */
	@Auto
	public float numberInHnStrict = 10;

	/**
	 * Same as numberInHnStrict, but for case, when there are
	 * more than one number in query
	 * */
	@Auto
	public float numbersInHn = 10;

	/**
	 * Term has numbers (but not only numbers) and searched
	 * among housenumber field
	 * */
	@Auto
	public float hasNumbersInHn = 5;
	
	/**
	 * Boost for exact name match
	 * */
	@Auto
	public float exactName = 10;

	/**
	 * Additional boost for numbers matched housenumber
	 * */
	@Auto
	public float numbersInHnOpt = 250;

	/**
	 * Debuff for numbers matched housenumber
	 * */
	@Auto
	public float nonStrictHNDebuf = 10000; 
	
	
	
	/**
	 * Read options from default place (config/search.weights)
	 * and merge them with default values.
	 * */
	public static Weights readFromFile() {
		
		Weights result = new Weights();
		
		try {
			Properties p = new Properties();
			p.load(new FileReader(new File(defLocation)));
			
			Field[] declaredFields = Weights.class.getDeclaredFields();
			for(Field f : declaredFields) {
				if(f.getAnnotation(Auto.class) != null) {
					read(p, f.getName(), result);
				}
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		
		return result;
	}

	/**
	 * Read and merge field
	 * */
	private static void read(Properties p, String name, Weights weights) throws Exception {
		
		Field field = weights.getClass().getField(name);
		
		String property = p.getProperty(name);
		if(property != null) {
			float val = Float.parseFloat(property);
			field.setFloat(weights, val);
		}
		
	}


}
