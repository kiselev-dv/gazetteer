package me.osm.gazetteer.utils.index;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.PriorityQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MMapBBIndex implements BinaryIndex {

	private static final Logger LOGGER = LoggerFactory.getLogger(MMapBBIndex.class);

	public static final boolean USE_READ_FOR_READONLY_FILE_ACCESS = true;

	public static final int PAGE_SIZE = 100000;

	public static final int SEARCH_PAGE_SIZE_BYTES = 1024 * 4;
	public static final int CACHE_SIZE_PAGES = 250;

	protected final LRUCache<Integer, CachePage> pagesCache = new LRUCache<>(CACHE_SIZE_PAGES);

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
				entry.getValue().flush();
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
			if (rows == Integer.MAX_VALUE) {
				throw new RuntimeException("Index is too big");
			}
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
	public int find(long key, Accessor accessor, IndexLineAccessMode mode) {
		int lower = 0;
		int upper = size() - 1;

		if(size() == 0) {
			return -1;
		}

		int index = lower + (upper - lower) / 2;

		while (true) {

			ByteBuffer guess = get(index, mode);
			long g = accessor.get(guess);

			if (g == key) {
				return index;
			}

			if (upper == lower) {
				return -1;
			}

			// integer division may fails to hit up boundary
			if (upper - lower == 1) {
				long upval = accessor.get(get(upper, mode));
				if (upval == key) {
					return upper;
				}
				long loval = accessor.get(get(lower, mode));
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
	public ByteBuffer get(int i, IndexLineAccessMode mode) {

		try {
			return getCachePage(i, mode).subBuffer(i);
		}
		catch (Exception e) {
			LOGGER.warn(e.getMessage());
			return getCachePage(i, mode).subBuffer(i);
		}

	}

	@Override
	public List<ByteBuffer> findAll(int index, long id, Accessor accessor, IndexLineAccessMode mode) {
		List<ByteBuffer> result = new ArrayList<ByteBuffer>();

		if(index >= 0 ) {
			result.add(get(index, mode));
			for(int i = 1; ;i++) {

				boolean lp = false;
				boolean ln = false;

				ByteBuffer lineP = getSafe(index + i, mode);
				if(lineP != null && accessor.get(lineP) == id) {
					result.add(lineP);
					lp = true;
				}

				ByteBuffer lineN = getSafe(index - i, mode);
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

	protected ByteBuffer getSafe(int i, IndexLineAccessMode mode) {
		if(i >= 0 && i < size()) {
			return get(i, mode);
		}
		return null;
	}

	@Override
	public int size() {
		return rows;
	}

	// Internals ------------------------------------

	private synchronized CachePage getCachePage(int i, IndexLineAccessMode mode) {

		int pageN = i / searchPageSize;

		int from = pageN * searchPageSize;
		int to =   pageN * searchPageSize + searchPageSize;

		from = Math.max(from, 0);
		to   = Math.min(to, size());

		CachePage page = this.pagesCache.get(from);

		if (page != null && mode != IndexLineAccessMode.IGNORE && page.mode != mode) {
			page = null;
			this.pagesCache.remove(from);
		}

		if (page != null) {
			this.hit++;
			return page;
		}

		this.miss++;

		try {

			MapMode fileAccessMode = MapMode.READ_ONLY;

			if (IndexLineAccessMode.IGNORE == mode) {
				// Access file for readwrite
				mode = IndexLineAccessMode.LINKED;
			}
			if (IndexLineAccessMode.LINKED == mode) {
				fileAccessMode = MapMode.READ_WRITE;
			}

			ByteBuffer cache;
			if (IndexLineAccessMode.UNLINKED == mode && USE_READ_FOR_READONLY_FILE_ACCESS) {
				byte[] buffer = new byte[(to - from) * rowLength];
				getRandomAccessFile().seek(from * rowLength);
				getRandomAccessFile().read(buffer);
				cache = ByteBuffer.wrap(buffer);
			}
			else {
				cache = getRandomAccessFile().getChannel().map(fileAccessMode,
						from * rowLength,
						(to - from) * rowLength);
			}

			CachePage cachePage = new CachePage(cache, from, to, mode);

			this.pagesCache.put(cachePage.from, cachePage);

			// Hit cache to update last accessed node
			this.pagesCache.get(cachePage.from);

			// strange issue, if I try to return this.pagesCache.get(cachePage.from)
			// it returns null, so it's bullet proof solution
			return cachePage;
		}
		catch (Exception e) {
			throw new RuntimeException("Can't read line " + i + " from " + this.indexFile, e);
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
			throw new RuntimeException(e);
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
		((Buffer)page).clear();
		for (ByteBuffer bb : bblist) {
			page.put(bb.array());
		}
		page.force();
	}

	private List<ByteBuffer> asByteBuffersList(MappedByteBuffer page) {
		((Buffer)page).rewind();
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
		FileChannel channel = file.getChannel();

		MappedByteBuffer buffer = channel.map(
				FileChannel.MapMode.READ_WRITE,
				fromRow * rowLength,
				(toRow - fromRow) * rowLength);

		return buffer;
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
				((Buffer)r).rewind();
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
			// Iterator doesn't allow rows changing
			return buf.get(index++, IndexLineAccessMode.UNLINKED);
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
		private final ByteBuffer buffer;
		private final IndexLineAccessMode mode;
		private final int from;
		private final int to;

		public CachePage(ByteBuffer buffer, int from, int to, IndexLineAccessMode mode) {
			this.buffer = mode == IndexLineAccessMode.LINKED ? buffer : ByteBuffer.wrap(buffer.array());
			this.from = from;
			this.to = to;
			this.mode = mode;

			if (buffer == null) {
				throw new BinaryIndexException("can't create cache page for null buffer");
			}
		}

		public ByteBuffer subBuffer(int i) {
			if(buffer != null && i >= from && i < to) {
				((Buffer)buffer).position((i - from) * rowLength);

				ByteBuffer subbufer = buffer.slice();
				((Buffer)subbufer).limit(rowLength);

				return subbufer;
			}

			if (buffer == null) {
				throw new BinaryIndexException("Can't get subbufer for row: " + i +
						" buffer is null");
			}

			throw new BinaryIndexException("Can't get subbufer for row: " + i +
					" buffer.from=" + from + " buffer.to=" + to +
					" rowLength=" + rowLength +
					" searchPageSize=" + searchPageSize +
					" indexSize=" + rows);
		}

		@Override
		public int hashCode() {
			return from;
		}

		@Override
		public boolean equals(Object obj) {
			return obj != null && obj.hashCode() == this.hashCode();
		}

		public void flush() {
			if (buffer instanceof MappedByteBuffer) {
				((MappedByteBuffer)buffer).force();
			}
		}
	}

	@Override
	public void close() {
		synchronize();

		LOGGER.info("Index hit/miss ratio: {}/{}", hit, miss);
		this.indexFile.delete();
	}

	@Override
	public synchronized void synchronize() {
		try {
			fileOutputStream.flush();
			fileOutputStream.close();
		}
		catch (ClosedChannelException e) {
			// Do nothing
		}
		catch (IOException ioe) {
			throw new RuntimeException(ioe);
		}

		synchronizeCache();
	}

	private synchronized void synchronizeCache() {
		try {
			if (!this.pagesCache.isEmpty()) {
				for (CachePage p : this.pagesCache.values()) {
					p.flush();
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
