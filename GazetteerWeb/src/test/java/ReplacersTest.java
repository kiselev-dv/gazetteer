import static org.junit.Assert.assertArrayEquals;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import me.osm.gazetteer.web.imp.Importer;
import me.osm.gazetteer.web.utils.OSMDocSinglton;
import me.osm.gazetteer.web.utils.ReplacersCompiler;

import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;


public class ReplacersTest {

	@Before
	public void setUp() throws Exception {
		OSMDocSinglton.initialize("jar");
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testEmpty() {
		Importer importer = new Importer(null, false);
		
		try {
			String[] asArray = importer.fuzzyHousenumberIndex("").toArray(new String[]{});
			assertArrayEquals(new String[]{}, asArray);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	public void testNumber() {
		Importer importer = new Importer(null, false);
		
		try {
			String[] asArray = importer.fuzzyHousenumberIndex("123").toArray(new String[]{});
			assertArrayEquals(new String[]{"123"}, asArray);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	public void testNumberAndLetter() {
		Importer importer = new Importer(null, false);
		
		try {
			Set<String> set = new HashSet<String>(importer.fuzzyHousenumberIndex("15A"));
			
			assert set.contains("15A");
			assert set.contains("15a");
			assert set.contains("15");
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	public void testNumberWithSlash() {
		Importer importer = new Importer(null, false);
		
		try {
			Set<String> set = new HashSet<String>(importer.fuzzyHousenumberIndex("15/123"));
			
			assert set.contains("15/123");
			assert set.contains("15");
			
			set = new HashSet<String>(importer.fuzzyHousenumberIndex("15A/123"));
			assert set.contains("15A/123");
			assert set.contains("15a");
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	public void testNumberAndLetterWithSuffix() {
		Importer importer = new Importer(null, false);
		
		try {
			Set<String> set = new HashSet<String>(importer.fuzzyHousenumberIndex("д. 15Aк1"));
			
			assert set.contains("д. 15Aк1");
			assert set.contains("15");
			assert set.contains("15a");
			assert set.contains("15a к1");
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
}
