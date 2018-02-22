package me.osm.gazetter.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.Buffer;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.Test;

import junit.framework.Assert;
import me.osm.gazetter.utils.index.BinaryIndex;
import me.osm.gazetter.utils.index.MMapBBIndex;
import me.osm.gazetter.utils.index.MMapIndexFactory;

public class MMapIndexTest {
	
	private static final class SecondIntComparator implements Comparator<ByteBuffer> {
		@Override
		public int compare(ByteBuffer o1, ByteBuffer o2) {
			try {
				((Buffer)o1).rewind();
				((Buffer)o2).rewind();
				
				o1.getInt();
				o2.getInt();
				
				int second1 = o1.getInt();
				int second2 = o2.getInt();
				
				return Integer.compare(second1, second2);
			}
			catch (BufferUnderflowException e) {
				throw e;
			}
		}
	}

	private static final int TEST_INDEX_SIZE = 1024 * 64;

	@Test
	public void testSequentialWrite() throws IOException {
		MMapIndexFactory factory = new MMapIndexFactory(null);
		MMapBBIndex index = (MMapBBIndex) factory.newByteIndex(4);
		
		for(int i = 0; i < TEST_INDEX_SIZE; i++) {
			ByteBuffer row = ByteBuffer.allocate(4);
			row.putInt(i);
			index.add(row);
		}
		
		index.synchronize();
		
		File file = index.getFile();
		FileInputStream fileInputStream = new FileInputStream(file);
		
		int j = 0;
		byte[] row = new byte[4];
		while(fileInputStream.read(row) == 4) {
			int i = ByteBuffer.wrap(row).getInt();
			Assert.assertEquals(j++, i);
		}
		
		Assert.assertEquals(j, TEST_INDEX_SIZE);
		
		fileInputStream.close();
	}
	
	@Test
	public void testSequentialRead() throws IOException {
		MMapIndexFactory factory = new MMapIndexFactory(null);
		MMapBBIndex index = (MMapBBIndex) factory.newByteIndex(4);
		
		for(int i = 0; i < TEST_INDEX_SIZE; i++) {
			ByteBuffer row = ByteBuffer.allocate(4);
			row.putInt(i);
			index.add(row);
		}
		
		index.synchronize();
		
		for(int i = 0; i < TEST_INDEX_SIZE; i++) {
			ByteBuffer byteBuffer = index.get(i);
			((Buffer)byteBuffer).rewind();
			Assert.assertEquals(i, byteBuffer.getInt()); 
		}
		
		index.close();
	}
	
	@Test
	public void testSortingInOneChunk() {
		BinaryIndex index = new MMapBBIndex(4 + 4, null) {
			
			@Override
			protected void writePage(MappedByteBuffer page, List<ByteBuffer> bblist) {
				int min = Integer.MIN_VALUE;
				for(ByteBuffer bb : bblist) {
					((Buffer)bb).rewind();
					bb.getInt();
					int i = bb.getInt();
					if (i >= min) {
						min = i;
					}
					else {
						throw new AssertionError("Page not sorted");
					}
					((Buffer)bb).rewind();
				}
				super.writePage(page, bblist);
			}
		};
		
		for(int i = 0; i < 1024 * 8; i++) {
			ByteBuffer row = ByteBuffer.allocate(4 + 4);
			row.putInt(i);
			int rnd = ThreadLocalRandom.current().nextInt();
			row.putInt(rnd);
			index.add(row);
		}
		
		index.sort(new SecondIntComparator());
		
		int min = Integer.MIN_VALUE;
		for(ByteBuffer bb : index) {
			((Buffer)bb).rewind();
			
			bb.getInt();
			int i = bb.getInt();
			if(i >= min) {
				min = i;
			}
			else {
				throw new AssertionError("Index not sorted");
			}
		}
		
		index.close();
	}
	
	@Test
	public void testSortingManyChunksPageWrite() {
		MMapBBIndex index = new MMapBBIndex(4 + 4, null) {
			
			@Override
			protected void writePage(MappedByteBuffer page, List<ByteBuffer> bblist) {
				int min = Integer.MIN_VALUE;
				for(ByteBuffer bb : bblist) {
					((Buffer)bb).rewind();
					bb.getInt();
					int i = bb.getInt();
					if (i >= min) {
						min = i;
					}
					else {
						throw new AssertionError("Page for write not sorted");
					}
					((Buffer)bb).rewind();
				}
				super.writePage(page, bblist);
			}
			
			@Override
			protected void sortInternal(Comparator<ByteBuffer> comparator) {
				try {
					try(RandomAccessFile raf = new RandomAccessFile(indexFile, "rw")) {
						partialySort(comparator, raf);
					}
					
				} catch (Exception e) {
					throw new RuntimeException("Cant sort " + this.indexFile, e);
				}
			}
			
		};
		
		for(int i = 0; i < MMapBBIndex.PAGE_SIZE * 2; i++) {
			ByteBuffer row = ByteBuffer.allocate(4 + 4);
			row.putInt(i);
			int rnd = ThreadLocalRandom.current().nextInt();
			row.putInt(rnd);
			index.add(row);
		}
		
		index.sort(new SecondIntComparator());
		
		int min = Integer.MIN_VALUE;
		int rc = 0;
		for(ByteBuffer bb : index) {
			((Buffer)bb).rewind();
			bb.getInt();
			int i = bb.getInt();
			if(i >= min) {
				min = i;
			}
			else {
				throw new AssertionError("Index not sorted");
			}
			if (++rc % MMapBBIndex.PAGE_SIZE == 0) {
				min = Integer.MIN_VALUE;
			}
		}
	}
	
	@Test
	public void testPageRewrite() throws Exception {
		File file = File.createTempFile("test", "");
		file.deleteOnExit();

		// Write
		{
			FileOutputStream fos = new FileOutputStream(file);
			
			for(int i = 0; i < 1024 * 4; i++ ) {
				ByteBuffer bb = ByteBuffer.allocate(4);
				bb.putInt(i);
				fos.write(bb.array());
			}
			
			fos.flush();
			fos.close();
		}
		
		// Check written
		{
			FileInputStream fis = new FileInputStream(file);
			byte[] introw = new byte[4];
			int j = 0;
			while(fis.read(introw) == 4) {
				int i = ByteBuffer.wrap(introw).getInt();
				Assert.assertEquals(j++, i);
			}
			
			fis.close();
		}

		// Rewrite page
		{
			int offset1 = 512;
			int length1 = 1024;
			
			RandomAccessFile raf = new RandomAccessFile(file, "rw");
			MappedByteBuffer map = raf.getChannel().map(MapMode.READ_WRITE, 4 * offset1, 4 * length1);
			int j = offset1;
			while (map.hasRemaining()) {
				int i = map.getInt();
				Assert.assertEquals(j++, i);
			}
			
			Assert.assertEquals(offset1 + length1, j);
			
			((Buffer)map).clear();
			
			for(int i = 0; i < length1; i++) {
				map.putInt(i);
			}
			
			map.force();
			raf.close();
		}
		
		// Check written
		{
			FileInputStream fis = new FileInputStream(file);
			byte[] introw = new byte[4];
			int j = 0;
			while(fis.read(introw) == 4 ) {
				int i = ByteBuffer.wrap(introw).getInt();
				int t = (j >= 512 && j < 512 + 1024) ? j - 512 : j;
				Assert.assertEquals(t, i);
				j++;
			}
			
			fis.close();
		}
		
		// Rewrite 2 pages
		{
			int offset1 = 0;
			int length1 = 1024;
			
			int offset2 = 1024 * 2;
			int length2 = 1024;
			
			RandomAccessFile raf = new RandomAccessFile(file, "rw");
			
			MappedByteBuffer map1 = raf.getChannel().map(MapMode.READ_WRITE, 4 * offset1, 4 * length1);
			MappedByteBuffer map2 = raf.getChannel().map(MapMode.READ_WRITE, 4 * offset2, 4 * length2);
			
			((Buffer)map1).clear();
			for(int i = 0; i < length1; i++) {
				map1.putInt(length1 - i);
			}
			map1.force();
			
			((Buffer)map2).clear();
			for(int i = 0; i < length2; i++) {
				map2.putInt(i * 2);
			}
			map2.force();
			
			raf.close();
		}
		
		// Check written
		{
			FileInputStream fis1 = new FileInputStream(file);
			FileInputStream fis2 = new FileInputStream(file);
			fis2.skip(1024 * 2 * 4);
			
			byte[] introw = new byte[4];
			for(int j = 0; j < 1024; j++) {
				fis1.read(introw);
				int i = ByteBuffer.wrap(introw).getInt();
				Assert.assertEquals(1024 - j, i);
			}
			
			for(int j = 0; j < 1024; j++) {
				fis2.read(introw);
				int i = ByteBuffer.wrap(introw).getInt();
				Assert.assertEquals(j * 2, i);
			}
			
			fis1.close();
			fis2.close();
		}
		
	}
	
	@Test
	public void testSortingManyChunks() {
		MMapBBIndex index = new MMapBBIndex(4 + 4, null);
		
		for(int i = 0; i < MMapBBIndex.PAGE_SIZE * 2; i++) {
			ByteBuffer row = ByteBuffer.allocate(4 + 4);
			row.putInt(i);
			int rnd = ThreadLocalRandom.current().nextInt();
			row.putInt(rnd);
			index.add(row);
		}
		
		index.sort(new SecondIntComparator());
		
		int min = Integer.MIN_VALUE;
		int rc = 0;
		for(ByteBuffer bb : index) {
			((Buffer)bb).rewind();
			bb.getInt();
			int i = bb.getInt();
			if(i >= min) {
				min = i;
			}
			else {
				throw new AssertionError("Index not sorted");
			}
			if (++rc % MMapBBIndex.PAGE_SIZE == 0) {
				min = Integer.MIN_VALUE;
			}
		}
	}
	
	@Test
	public void testRowsSynchronization() {
		MMapBBIndex index = new MMapBBIndex(4 + 4, null);
		
		for(int i = 0; i < MMapBBIndex.SEARCH_PAGE_SIZE_BYTES * MMapBBIndex.CACHE_SIZE_PAGES * 2; i++) {
			ByteBuffer row = ByteBuffer.allocate(4 + 4);
			row.putInt(i);
			int rnd = ThreadLocalRandom.current().nextInt();
			row.putInt(rnd);
			index.add(row);
		}
		
		index.sort(new SecondIntComparator());

		// Update rows
		{
			int i = 0;
			for(ByteBuffer bb : index) {
				bb.putInt(i++);
			}
		}
		
		index.synchronize();
		
		// Check
		{
			int i = 0;
			for(ByteBuffer bb : index) {
				Assert.assertEquals(i++, bb.getInt());
			}
		}
		
		index.close();
	}
	
}
