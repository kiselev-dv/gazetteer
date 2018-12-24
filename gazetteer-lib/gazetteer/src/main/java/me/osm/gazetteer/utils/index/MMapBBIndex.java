package me.osm.gazetteer.utils.index;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.PriorityQueue;

import org.slf4j.LoggerFactory;

public class MMapBBIndex implements BinaryIndex {

	public static final int PAGE_SIZE = 100000;

	public static final int SEARCH_PAGE_SIZE_BYTES = 1024 * 4;
	public static final int CACHE_SIZE_PAGES = 250;

	protected LRUCache<Integer, CachePage> pagesCache = new LRUCache<>(CACHE_SIZE_PAGES);

	protected int rowLength = 0;
	protected int rows = 0;

	protected File indexFile;
	protected OutputStream fileOutputStream;

	protected int searchPageSize;

	protected long hit = 0;
	protected long miss = 0;

	private RandomAccessFile indexRandomAccessFile;


	public MMapBBIndex(int rowLength, File tempDir) {
		this.searchPageSize = SEARCH_PAGE_SIZE_BYTES / rowLength;
		this.rowLength = rowLength;

		pagesCache.setEvictionVisitor(new EvictionVisitor<Integer, MMapBBIndex.CachePage>() {

			@Override
			public void onEviction(Entry<Integer, CachePage> entry) {
				try {
					closeCachePage(entry.getValue());
				}
				catch (IOException e) {
					throw new RuntimeException(e);
				}
			}

		});

		try {
			this.indexFile = File.createTempFile("indx-", "_" + rowLength + ".bin", tempDir);
			this.fileOutputStream = new BufferedOutputStream(new FileOutputStream(indexFile));

			this.indexFile.deleteOnExit();

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void add(ByteBuffer bb) {
		try {
			rows++;
			fileOutputStream.write(bb.array());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void sort(Comparator<ByteBuffer> comparator) {
		synchronize();
		sortInternal(comparator);
	}

	@Override
	public int find(long key, Accessor accessor) {
		int lower = 0;
		int upper = size() - 1;

		if(size() == 0) {
			return -1;
		}

		int index = lower + (upper - lower) / 2;

		while (true) {

			ByteBuffer guess = get(index);
			long g = accessor.get(guess);

			if (g == key) {
				return index;
			}

			if (upper == lower) {
				return -1;
			}

			// integer division may fails to hit up boundary
			if (upper - lower == 1) {
				long upval = accessor.get(get(upper));
				if (upval == key) {
					return upper;
				}
				long loval = accessor.get(get(lower));
				if (loval == key) {
					return lower;
				}
				return -1;
			}

			// Guess lies to the right, get index to the left
			if (key < g) {
				upper = index;
			}
			else {
				lower = index;
			}
			index = lower + (upper - lower) / 2;
		}
	}

	@Override
	public Iterator<ByteBuffer> iterator() {
		return new MMapBufferIterator(this);
	}

	public Iterator<ByteBuffer> iterator(int offset) {
		return new MMapBufferIterator(this, offset);
	}

	@Override
	public ByteBuffer get(int i) {

		CachePage page = getCachePage(i);

		if (i >= page.from && i < page.to) {
			page.buffer.position((i - page.from) * rowLength);

			ByteBuffer subbufer = page.buffer.slice();
			subbufer.limit(rowLength);

			return subbufer;
		}
		else {
			throw new RuntimeException();
		}

	}

	@Override
	public List<ByteBuffer> findAll(int index, long id, Accessor accessor) {
		List<ByteBuffer> result = new ArrayList<ByteBuffer>();

		if(index >= 0 ) {
			result.add(get(index));
			for(int i = 1; ;i++) {

				boolean lp = false;
				boolean ln = false;

				ByteBuffer lineP = getSafe(index + i);
				if(lineP != null && accessor.get(lineP) == id) {
					result.add(lineP);
					lp = true;
				}

				ByteBuffer lineN = getSafe(index - i);
				if(lineN != null && accessor.get(lineN) == id) {
					result.add(lineN);
					ln = true;
				}

				if(!lp && !ln) {
					break;
				}

			}
		}

		return result;
	}

	protected ByteBuffer getSafe(int i) {
		if(i >= 0 && i < size()) {
			return get(i);
		}
		return null;
	}

	@Override
	public int size() {
		return rows;
	}

	// Internals ------------------------------------

	private CachePage getCachePage(int i) {

		int pageN = i / searchPageSize;

		int from = pageN * searchPageSize;
		int to =   pageN * searchPageSize + searchPageSize;


		CachePage page = this.pagesCache.get(from);
		if (page != null) {
			this.hit++;
			return page;
		}

		synchronized(this.pagesCache) {

			page = this.pagesCache.get(from);
			if (page != null) {
				this.hit++;
				return page;
			}

			this.miss++;

			CachePage cachePage = new CachePage();
			cachePage.from = Math.max(from, 0);
			cachePage.to   = Math.min(to, size());

			try {
				MappedByteBuffer cache = getRandomAccessFile().getChannel().map(MapMode.READ_WRITE,
						cachePage.from * rowLength,
						(cachePage.to - cachePage.from) * rowLength);
				cachePage.buffer = cache;

				this.pagesCache.put(cachePage.from, cachePage);

				// Hit cache to update last accessed node
				return this.pagesCache.get(from);
			}
			catch (Exception e) {
				throw new RuntimeException("Can't read line " + i + " from " + this.indexFile, e);
			}
		}
	}

	private RandomAccessFile getRandomAccessFile() {
		try {
			if (this.indexRandomAccessFile == null) {
				this.indexRandomAccessFile = new RandomAccessFile(this.indexFile, "rw");
			}
			return this.indexRandomAccessFile;
		}
		catch (FileNotFoundException e) {
			throw new RuntimeException();
		}
	}

	protected void sortInternal(Comparator<ByteBuffer> comparator) {
		try {

			try(RandomAccessFile raf = new RandomAccessFile(indexFile, "rw")) {
				partialySort(comparator, raf);
			}

			if (rows > PAGE_SIZE) {

				List<BinaryFileBuffer> buffers = new ArrayList<>();

				for (int p = 0; p < rows / PAGE_SIZE + 1; p++) {
					int rowFrom = p * PAGE_SIZE;
					int rowTo = Math.min(p * PAGE_SIZE + PAGE_SIZE, rows);

					buffers.add(new BinaryFileBuffer(
							(MMapBufferIterator) this.iterator(rowFrom),
							rowTo - rowFrom));
				}


				File tmp = new File(this.indexFile.toString() + ".srtd");
				OutputStream tmpOut = new BufferedOutputStream(new FileOutputStream(tmp));
				mergeSorted(tmpOut, comparator, buffers);

				tmpOut.flush();
				tmpOut.close();

				this.pagesCache.clear();
				this.indexRandomAccessFile.close();
				this.indexRandomAccessFile = null;

				this.indexFile.delete();
				tmp.renameTo(this.indexFile);
			}

		} catch (Exception e) {
			throw new RuntimeException("Cant sort " + this.indexFile, e);
		}
	}

	protected void partialySort(
			Comparator<ByteBuffer> comparator, RandomAccessFile file) throws IOException {

		for (int p = 0; p <= rows / PAGE_SIZE; p++) {
			MappedByteBuffer page = readPage(file, p, PAGE_SIZE);
			List<ByteBuffer> bblist = asByteBuffersList(page);
			Collections.sort(bblist, comparator);
			writePage(page, bblist);
		}
	}

	protected void writePage(MappedByteBuffer page, List<ByteBuffer> bblist) {
		page.clear();
		for (ByteBuffer bb : bblist) {
			page.put(bb.array());
		}
		page.force();
	}

	private List<ByteBuffer> asByteBuffersList(MappedByteBuffer page) {
		page.rewind();
		List<ByteBuffer> result = new ArrayList<>();
		while(page.hasRemaining()) {
			byte[] rowBuffer = new byte[this.rowLength];
			page.get(rowBuffer);
			ByteBuffer row = ByteBuffer.wrap(rowBuffer);
			result.add(row);
		}
		return result;
	}

	private MappedByteBuffer readPage(RandomAccessFile file, int page, int size) throws IOException {
		int fromRow = page * size;
		int toRow = page * size + size;
		toRow = Math.min(toRow, rows);
		return file.getChannel().map(FileChannel.MapMode.READ_WRITE, fromRow * rowLength, (toRow - fromRow) * rowLength);
	}

	public int mergeSorted(OutputStream fbw, final Comparator<ByteBuffer> cmp,
			List<BinaryFileBuffer> buffers) throws IOException {

		PriorityQueue<BinaryFileBuffer> pq = new PriorityQueue<BinaryFileBuffer>(11,
				new BinaryFileBufferComparator(cmp));

		for (BinaryFileBuffer bfb : buffers)
			if (!bfb.empty())
				pq.add(bfb);

		int rowcounter = 0;
		try {
			while (pq.size() > 0) {
				BinaryFileBuffer bfb = pq.poll();
				ByteBuffer r = bfb.pop();
				r.rewind();
				while (r.hasRemaining()) {
					fbw.write(r.get());
				}
				++rowcounter;
				if (!bfb.empty()) {
					pq.add(bfb);
				}
			}
		} finally {
			fbw.close();
		}
		return rowcounter;

	}

	private static final class MMapBufferIterator implements Iterator<ByteBuffer> {
		private int index = 0;
		private MMapBBIndex buf;

		public MMapBufferIterator(MMapBBIndex mMapByteBuffer) {
			this.buf = mMapByteBuffer;
			this.index = 0;
		}

		public MMapBufferIterator(MMapBBIndex mMapByteBuffer, int index) {
			this.buf = mMapByteBuffer;
			this.index = index;
		}

		@Override
		public boolean hasNext() {
			return index < buf.size();
		}

		@Override
		public ByteBuffer next() {
			return buf.get(index++);
		}
	}

	private static final class BinaryFileBufferComparator implements Comparator<BinaryFileBuffer> {
		private final Comparator<ByteBuffer> cmp;

		private BinaryFileBufferComparator(Comparator<ByteBuffer> cmp) {
			this.cmp = cmp;
		}

		@Override
		public int compare(BinaryFileBuffer i, BinaryFileBuffer j) {
			return cmp.compare(i.peek(), j.peek());
		}
	}

	/**
	 * This is essentially a thin wrapper on top of a BufferedReader... which keeps
	 * the last line in memory.
	 *
	 * @author Daniel Lemire
	 */
	protected final class BinaryFileBuffer {

		public MMapBufferIterator fbr;
		private ByteBuffer cache;

		private int rows;
		private int rowsRed;

		public BinaryFileBuffer(MMapBufferIterator r, int rows) throws IOException {
			this.fbr = r;
			this.rows = rows;
			this.rowsRed = 0;
			reload();
		}

		public void close() {
		}

		public boolean empty() {
			return this.cache == null;
		}

		public ByteBuffer peek() {
			return this.cache;
		}

		public ByteBuffer pop() throws IOException {
			ByteBuffer answer = peek();// make a copy
			reload();
			return answer;
		}

		private void reload() throws IOException {
			if (rowsRed < rows) {
				this.cache = this.fbr.next();
				this.rowsRed++;
			}
			else {
				this.cache = null;
			}
		}
	}

	private final class CachePage {
		public MappedByteBuffer buffer;
		private int from;
		private int to;

		@Override
		public int hashCode() {
			return from;
		}

		@Override
		public boolean equals(Object obj) {
			return obj != null && obj.hashCode() == this.hashCode();
		}
	}

	@Override
	public void close() {
		synchronize();

		LoggerFactory.getLogger(getClass()).info("Index hit/miss ratio: {}/{}", hit, miss);
		this.indexFile.delete();
	}

	private void closeCachePage(CachePage cachePage) throws IOException {
		cachePage.buffer.force();
	}

	@Override
	public void synchronize() {
		try {
			fileOutputStream.flush();
			fileOutputStream.close();

			if (!this.pagesCache.isEmpty()) {
				for (CachePage p : this.pagesCache.values()) {
					closeCachePage(p);
				}
				this.pagesCache.clear();
			}

			if(this.indexRandomAccessFile != null) {
				this.indexRandomAccessFile.close();
				this.indexRandomAccessFile = null;
			}

		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public File getFile() {
		return indexFile;
	}

}
