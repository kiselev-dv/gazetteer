package me.osm.gazetter.diff;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import me.osm.gazetter.diff.indx.ByteUtils;
import me.osm.gazetter.diff.indx.DiffMapHashMapIndex;
import me.osm.gazetter.diff.indx.DiffMapIndex;
import me.osm.gazetter.diff.indx.ByteUtils.IdParts;
import me.osm.gazetter.diff.indx.DiffMapIndex.DiffMapIndexRow;
import me.osm.gazetter.diff.readers.DiffNewFileFirstPassReader;
import me.osm.gazetter.diff.readers.DiffOldFileFirstPassReader;
import me.osm.gazetter.diff.readers.DiffOldFileSecondPassReader;
import me.osm.gazetter.striper.GeoJsonWriter;
import me.osm.gazetter.utils.FileUtils;
import me.osm.gazetter.utils.FileUtils.LineHandler;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Calculate difference between two gazetteer json dumps
 * 
 * @author dkiselev
 */
public class Diff {
	
	private static final class IdLengthHandler implements LineHandler {
		private final int[] maxLength;

		private IdLengthHandler(int[] maxLength) {
			this.maxLength = maxLength;
		}

		@Override
		public void handle(String s) {
			String id = GeoJsonWriter.getId(s);
			try {
				if (id != null) {
					IdParts idParts = ByteUtils.parse(id);
					ByteBuffer bb = ByteUtils.encode(idParts);
					maxLength[0] = Math.max(maxLength[0], bb.capacity());
					
					IdParts idPartsDecoded = ByteUtils.decode(bb, idParts.type);
					String idDecoded = idPartsDecoded.type + "-" + StringUtils.join(idPartsDecoded.parts, '-');
					if (idPartsDecoded.tail != null) {
						idDecoded += "--" + idPartsDecoded.tail; 
					}
					
					if (!id.startsWith(idDecoded)) {
						log.error("Decoded id " + idDecoded + " doesn't match id " + id);
					}
				}
			}
			catch (Exception e) {
				throw new RuntimeException("Failed to convert as bytes " + id, e);
			}
		}
	}

	private static final Logger log = LoggerFactory.getLogger(Diff.class); 
	
	private String oldPath;
	private String newPath;

	private PrintWriter out;
	
	private boolean fillOld = false;

	private PrintWriter outTmp;

	private boolean keyLenghtOnly = false;
	
	private static final DateTimeZone timeZone = DateTimeZone.getDefault();
	
	private DiffMapIndex map = null;

	/**
	 * @param oldPath path to old file
	 * @param newPath path to new file
	 * @param out where should we write output
	 * @param fillOld fill full information for old objects
	 */
	public Diff(String oldPath, String newPath, String out, 
			boolean fillOld, boolean diskIndex, boolean keyLenghtOnly) {
		
		this.oldPath = oldPath;
		this.newPath = newPath;
		this.fillOld = fillOld;
		
		this.keyLenghtOnly = keyLenghtOnly;

		if (diskIndex) {
			map = new DiffMapHashMapIndex();
		}
		else {
			throw new UnsupportedOperationException("Disk index not yet implemented");
		}
		
		try {
			this.outTmp = FileUtils.getPrintWriter(tmpFile, false);

			if(out.equals("-")) {
				this.out = new PrintWriter(System.out);
			}
			else {
				this.out = FileUtils.getPrintWriter(new File(out), false);
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private File tmpFile = new File("diff.tmp.gz");
	
	/**
	 * Run task
	 */
	public void run() {
		
		final Counters counters = new Counters();
		
		try {
			if (this.keyLenghtOnly) {
				log.info("Key length only mode, diff will not be generated");
				
				final int[] maxLength = new int[]{-1};
				FileUtils.handleLines(new File(this.oldPath), new IdLengthHandler(maxLength) );
				FileUtils.handleLines(new File(this.newPath), new IdLengthHandler(maxLength) );
				
				log.info("Key max length in bytes {}", maxLength);
				
				return;
			}

			log.info("Read {}", oldPath);
			
			FileUtils.handleLines(new File(this.oldPath), 
					new DiffOldFileFirstPassReader(map, counters));
			
			map.buildIndex();
			
			log.info("{} lines were readed", map.size());

			String generationTimestamp = LocalDateTime.now().toDateTime(timeZone).toInstant().toString();
			out.println("@diff-generation-ts " + generationTimestamp);
			
			out.println("@old-file " + oldPath);
			out.println("@new-file " + newPath);

			out.println("@old-timestamp " + counters.oldTs.toInstant().toString());
			out.println("@old-hash " + counters.oldHash);
			
			final Set<String> olds = new HashSet<String>();
			
			FileUtils.handleLines(new File(this.newPath), 
					new DiffNewFileFirstPassReader(map, outTmp, olds, counters));
			
			// Можно пересортировать подняв вверх но только для обычных файлов.
			// Либо читать новый еще раз, чтобы записать метаданные в начало файла
			// Есть еще вариант записать все в конец, но это неудобно для потокового чтения.
			
			out.println("@new-timestamp " + counters.newTs.toInstant().toString());
			out.println("@new-hash " + counters.newHash);
			
			// В мапе сейчас содержатся только те объекты 
			// которых нет в --new
			
			// В olds сотержаться айдишки тех, кто есть в --old и они новее
			// по идее таких быть не должно, кроме случая если перепутали
			// --new и --old местами
			
			map.cleanRemoved();
			
			boolean rowsRemains = map.size() > 0; 
			boolean newer = !olds.isEmpty();
			
			if(newer) {
				log.warn("There are objects in --old with newer timestamps");
			}
			
			if(rowsRemains || newer) {
				if(fillOld) {
					FileUtils.handleLines(new File(this.oldPath), 
							new DiffOldFileSecondPassReader(map, outTmp, olds, counters));
				}
				else {
					Iterator<DiffMapIndexRow> ri = map.rowsIterator();
					while(ri.hasNext()) {
						DiffMapIndexRow row = ri.next();
						outTmp.println("- " + "{\"id\":\"" + row.key + "\"}");
						counters.remove++;
					}
					
					for(String id : olds) {
						outTmp.println("O " + "{\"id\":\"" + id + "\"}");
						counters.takeOld++;
					}
				}
			}
			
			outTmp.flush();
			outTmp.close();
			
			InputStream is = FileUtils.getFileIS(tmpFile);
			IOUtils.copy(is, out);
			is.close();
			
			tmpFile.delete();
			
			out.flush();
			out.close();
			
			log.info("Remove {}", counters.remove);
			log.info("Add {}", counters.add);
			log.info("Take new {}", counters.takeNew);
			log.info("Take old {}", counters.takeOld);
			
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
