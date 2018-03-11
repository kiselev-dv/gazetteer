package me.osm.gazetter.test;

import java.nio.ByteBuffer;

import org.junit.Test;

import junit.framework.Assert;
import me.osm.gazetter.diff.indx.ByteUtils;
import me.osm.gazetter.diff.indx.ByteUtils.IdParts;

public class DiffByteUtilsTest {

	@Test
	public void testIdToString1() {
		testId("poipnt-3433984807-n1411754920--", false);
	}
	
	@Test
	public void testIdToString2() {
		testId("adrpnt-0923983947-w266856538--regular");
	}
	
	@Test
	public void testIdToString3() {
		testId("hghway-1797057630-w296395297--m1303126467");
	}
	
	@Test
	public void testIdToString4() {
		testId("poipnt-1267662723-r3606459-w238217077--regular");
	}
	
	@Test
	public void testIdToString5() {
		testId("adrpnt-2760659966-r6629425--regular");
	}
	
	@Test
	public void testIdToStringInterpolation() {
		testId("adrpnt-4101949466-i221067676-10--regular");
	}
	
	@Test
	public void testIdToStringInterpolation2() {
		testId("poipnt-2301635769-w52587848-i385876626--regular");
	}
	
	public void testId(String id) {
		testId(id, true);
	}
	
	public void testId(String id, boolean strict) {
		IdParts idParts = ByteUtils.parse(id);
		ByteBuffer bb = ByteUtils.encode(idParts);
		
		IdParts idPartsDecoded = ByteUtils.decode(bb, idParts.type);
		String idDecoded = ByteUtils.joinToId(idPartsDecoded);
		
		if (!id.startsWith(idDecoded)) {
			throw new AssertionError("Decoded id " + idDecoded + " doesn't match id " + id);
		}

		if (strict) {
			Assert.assertEquals(id, idDecoded);
		}
	}
	
}
