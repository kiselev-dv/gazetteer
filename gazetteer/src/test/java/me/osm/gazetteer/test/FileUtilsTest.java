package me.osm.gazetteer.test;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;

import me.osm.gazetteer.utils.FileUtils;

public class FileUtilsTest {

	private static final class MyComparator implements Comparator<String> {

		@Override
		public int compare(String line, String prefix) {
			int lineVaue = Integer.valueOf(StringUtils.split(line)[0]);
			int prefixVaue = Integer.valueOf(prefix);
			return Integer.compare(lineVaue, prefixVaue);
		}

	}

	private static final MyComparator CMP_INST = new MyComparator();

	@Test
	public void testFileBinarySearch1() throws IOException {
		ClassLoader classLoader = getClass().getClassLoader();
		File file = new File(classLoader.getResource("binary-search-test.txt").getFile());
		RandomAccessFile raf = new RandomAccessFile(file, "r");
		List<String> binarySearch = FileUtils.binarySearch(raf, "1", CMP_INST);

		Assert.assertEquals(3, binarySearch.size());

		Assert.assertTrue("Contains: 1 Line 1", binarySearch.contains("1 Line 1"));
		Assert.assertTrue("Contains: 1 Line 1 copy", binarySearch.contains("1 Line 1 copy"));
		Assert.assertTrue("Contains: 1 Line one again", binarySearch.contains("1 Line one again"));

		raf.close();
	}

	@Test
	public void testFileBinarySearch2() throws IOException {
		ClassLoader classLoader = getClass().getClassLoader();
		File file = new File(classLoader.getResource("binary-search-test.txt").getFile());
		RandomAccessFile raf = new RandomAccessFile(file, "r");
		List<String> binarySearch = FileUtils.binarySearch(raf, "2", CMP_INST);

		Assert.assertEquals(2, binarySearch.size());

		raf.close();
	}

	@Test
	public void testFileBinarySearch5() throws IOException {
		ClassLoader classLoader = getClass().getClassLoader();
		File file = new File(classLoader.getResource("binary-search-test.txt").getFile());
		RandomAccessFile raf = new RandomAccessFile(file, "r");
		List<String> binarySearch = FileUtils.binarySearch(raf, "5", CMP_INST);

		Assert.assertEquals(5, binarySearch.size());
		Assert.assertTrue("Contains: 5 line 5", binarySearch.contains("5 line 5"));

		raf.close();
	}

	@Test
	public void testFileBinarySearch12() throws IOException {
		ClassLoader classLoader = getClass().getClassLoader();
		File file = new File(classLoader.getResource("binary-search-test.txt").getFile());
		RandomAccessFile raf = new RandomAccessFile(file, "r");
		List<String> binarySearch = FileUtils.binarySearch(raf, "12", CMP_INST);

		Assert.assertEquals(1, binarySearch.size());
		Assert.assertTrue("Contains: 12 line 12", binarySearch.contains("12 line 12"));

		raf.close();
	}

	@Test
	public void testFileBinarySearchEmpty() throws IOException {
		ClassLoader classLoader = getClass().getClassLoader();
		File file = new File(classLoader.getResource("binary-search-test.txt").getFile());
		RandomAccessFile raf = new RandomAccessFile(file, "r");
		List<String> binarySearch = FileUtils.binarySearch(raf, "45", CMP_INST);

		Assert.assertEquals(0, binarySearch.size());

		raf.close();
	}

}
