package me.osm.gazetter.diff;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import me.osm.gazetter.utils.FileUtils;

import org.apache.commons.io.IOUtils;
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
	
	private static final Logger log = LoggerFactory.getLogger(Diff.class); 
	
	private String oldPath;
	private String newPath;

	private String oldHeader;
	private String newHeader;
	
	private PrintWriter out;
	
	private boolean fillOld = false;

	private PrintWriter outTmp;
	
	private static final DateTimeZone timeZone = DateTimeZone.getDefault();
	
	/**
	 * @param oldPath path to old file
	 * @param newPath path to new file
	 * @param out where should we write output
	 * @param fillOld fill full information for old objects
	 */
	public Diff(String oldPath, String newPath, String out, boolean fillOld) {
		
		this.oldPath = oldPath;
		this.newPath = newPath;
		this.fillOld = fillOld;

		
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

	private TreeMap<String, Object[]> map = new TreeMap<>();

	private File tmpFile = new File("diff.tmp.gz");
	
	/**
	 * Run task
	 */
	public void run() {
		
		final Counters counters = new Counters();
		
		try {
			log.info("Read {}", oldPath);
			
			FileUtils.handleLines(new File(this.oldPath), 
					new DiffOldFileFirstPassReader(map, counters));
			
			log.info("{} lines were readed", map.size());

			String generationTimestamp = LocalDateTime.now().toDateTime(timeZone).toInstant().toString();
			out.println("@diff-generation-ts " + generationTimestamp);
			
			out.println("@old-file " + oldPath);
			out.println("@new-file " + newPath);

			if(oldHeader != null) {
				out.println("@old-file-header " + oldHeader);
			}

			if(newHeader != null) {
				out.println("@new-file-header " + newHeader);
			}

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
			
			boolean removed = !map.isEmpty();
			boolean newer = !olds.isEmpty();
			
			if(newer) {
				log.warn("There are objects in --old with newer timestamps");
			}
			
			if(removed || newer) {
				if(fillOld) {
					FileUtils.handleLines(new File(this.oldPath), 
							new DiffOldFileSecondPassReader(map, outTmp, olds, counters));
				}
				else {
					for(Entry<String, Object[]> entry : map.entrySet()) {
						outTmp.println("- " + "{\"id\":\"" + entry.getKey() + "\"}");
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

	/**
	 * Write header for old file
	 * 
	 * @return header
	 */
	public String getOldHeader() {
		return oldHeader;
	}

	/**
	 * Write header for old file
	 * 
	 * @param oldHeader
	 */
	public void setOldHeader(String oldHeader) {
		this.oldHeader = oldHeader;
	}

	/**
	 * Write header for new file
	 * 
	 * @return header
	 */
	public String getNewHeader() {
		return newHeader;
	}

	/**
	 * Write header for new file
	 * 
	 * @param newHeader
	 */
	public void setNewHeader(String newHeader) {
		this.newHeader = newHeader;
	}
	
}
