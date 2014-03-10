package me.osm.gazetter.test;

import static org.junit.Assert.*;
import me.osm.gazetter.striper.builders.PlacePointsBuilder;
import me.osm.gazetter.striper.builders.PlacePointsBuilder.BBOX;

import org.junit.Test;

public class PlacePointsBuilderTest {

	@Test
	public void testMoveToAndBack() {
		assertEquals(-90.0, PlacePointsBuilder.moveTo(0.0), 0.001);
		assertEquals(0.0, PlacePointsBuilder.moveBack(-90.0), 0.001);
		
		assertEquals(170.0, PlacePointsBuilder.moveTo(-100), 0.001);
		assertEquals(-100.0, PlacePointsBuilder.moveBack(170.0), 0.001);
		
		assertEquals(80.0, PlacePointsBuilder.moveTo(170), 0.001);
		assertEquals(170.0, PlacePointsBuilder.moveBack(80.0), 0.001);
		
		assertEquals(100, PlacePointsBuilder.moveTo(-170), 0.001);
		assertEquals(-170.0, PlacePointsBuilder.moveBack(100), 0.001);
	}

	@Test
	public void testBBOXDx() {
		
		BBOX bbox = new BBOX();
		
		bbox.extend(0.0, 0.0);
		bbox.extend(10.0, 0.0);
		
		assertEquals(10.0, bbox.getDX(), 0.001);
	}

	@Test
	public void testBBOX180() {
		
		BBOX bbox = new BBOX();
		
		bbox.extend(0.0, 0.0);
		bbox.extend(170.0, 0.0);
		bbox.extend(-170.0, 0.0);
		
		assertEquals(340.0, bbox.getDX(), 0.001);
	}

	@Test
	public void testBBOX180Translated() {
		
		BBOX bbox = new BBOX();
		
		bbox.extend(PlacePointsBuilder.moveTo(170.0), 0.0);
		bbox.extend(PlacePointsBuilder.moveTo(-170.0), 0.0);
		
		assertEquals(20.0, bbox.getDX(), 0.001);
		
		bbox.extend(PlacePointsBuilder.moveTo(0.0), 0.0);
		assertEquals(190.0, bbox.getDX(), 0.001);
	}
	
	

}
