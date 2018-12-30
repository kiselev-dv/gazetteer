package com.google.code.externalsorting;

// filename: ExternalSort.java
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Goal: offer a generic external-memory sorting program in Java.
 * 
 * It must be : - hackable (easy to adapt) - scalable to large files - sensibly
 * efficient.
 * 
 * This software is in the public domain.
 * 
 * Usage: java com/google/code/externalsorting/ExternalSort somefile.txt out.txt
 * 
 * You can change the default maximal number of temporary files with the -t
 * flag: java com/google/code/externalsorting/ExternalSort somefile.txt out.txt
 * -t 3
 * 
 * For very large files, you might want to use an appropriate flag to allocate
 * more memory to the Java VM: java -Xms2G
 * com/google/code/externalsorting/ExternalSort somefile.txt out.txt
 * 
 * By (in alphabetical order) Philippe Beaudoin, Eleftherios Chetzakis, Jon
 * Elsas, Christan Grant, Daniel Haran, Daniel Lemire, Sugumaran Harikrishnan,
 * Amit Jain, Thomas Mueller, Jerry Yang, First published: April 2010 originally posted at
 * http://lemire.me/blog/archives/2010/04/01/external-memory-sorting-in-java/
 */
public class ExternalSort {

        /**
         * 
         */
        public static void displayUsage() {
                System.out
                        .println("java com.google.externalsorting.ExternalSort inputfile outputfile");
                System.out.println("Flags are:");
                System.out.println("-v or --verbose: verbose output");
                System.out.println("-d or --distinct: prune duplicate lines");
                System.out
                        .println("-t or --maxtmpfiles (followed by an integer): specify an upper bound on the number of temporary files");
                System.out
                        .println("-c or --charset (followed by a charset code): specify the character set to use (for sorting)");
                System.out
                        .println("-z or --gzip: use compression for the temporary files");
                System.out
                        .println("-H or --header (followed by an integer): ignore the first few lines");
                System.out
                        .println("-s or --store (following by a path): where to store the temporary files");
                System.out.println("-h or --help: display this message");
        }

        /**
         * This method calls the garbage collector and then returns the free
         * memory. This avoids problems with applications where the GC hasn't
         * reclaimed memory and reports no available memory.
         * 
         * @return available memory
         */
        public static long estimateAvailableMemory() {
                System.gc();
                return Runtime.getRuntime().freeMemory();
        }

        /**
         * we divide the file into small blocks. If the blocks are too small, we
         * shall create too many temporary files. If they are too big, we shall
         * be using too much memory.
         * 
         * @param sizeoffile
         *                how much data (in bytes) can we expect
         * @param maxtmpfiles
         *                how many temporary files can we create (e.g., 1024)
         * @param maxMemory
         *                Maximum memory to use (in bytes)
         * @return the estimate
         */
        public static long estimateBestSizeOfBlocks(final long sizeoffile,
                final int maxtmpfiles, final long maxMemory) {
                // we don't want to open up much more than maxtmpfiles temporary
                // files, better run
                // out of memory first.
                long blocksize = sizeoffile / maxtmpfiles
                        + (sizeoffile % maxtmpfiles == 0 ? 0 : 1);

                // on the other hand, we don't want to create many temporary
                // files
                // for naught. If blocksize is smaller than half the free
                // memory, grow it.
                if (blocksize < maxMemory / 2) {
                        blocksize = maxMemory / 2;
                }
                return blocksize;
        }

        /**
         * @param args
         * @throws IOException
         */
        public static void main(final String[] args) throws IOException {
                boolean verbose = false;
                boolean distinct = false;
                int maxtmpfiles = DEFAULTMAXTEMPFILES;
                Charset cs = Charset.defaultCharset();
                String inputfile = null, outputfile = null;
                File tempFileStore = null;
                boolean usegzip = false;
                int headersize = 0;
                for (int param = 0; param < args.length; ++param) {
                        if (args[param].equals("-v")
                                || args[param].equals("--verbose")) {
                                verbose = true;
                        } else if ((args[param].equals("-h") || args[param]
                                .equals("--help"))) {
                                displayUsage();
                                return;
                        } else if ((args[param].equals("-d") || args[param]
                                .equals("--distinct"))) {
                                distinct = true;
                        } else if ((args[param].equals("-t") || args[param]
                                .equals("--maxtmpfiles"))
                                && args.length > param + 1) {
                                param++;
                                maxtmpfiles = Integer.parseInt(args[param]);
                                if (headersize < 0) {
                                        System.err
                                                .println("maxtmpfiles should be positive");
                                }
                        } else if ((args[param].equals("-c") || args[param]
                                .equals("--charset"))
                                && args.length > param + 1) {
                                param++;
                                cs = Charset.forName(args[param]);
                        } else if ((args[param].equals("-z") || args[param]
                                .equals("--gzip"))) {
                                usegzip = true;
                        } else if ((args[param].equals("-H") || args[param]
                                .equals("--header")) && args.length > param + 1) {
                                param++;
                                headersize = Integer.parseInt(args[param]);
                                if (headersize < 0) {
                                        System.err
                                                .println("headersize should be positive");
                                }
                        } else if ((args[param].equals("-s") || args[param]
                                .equals("--store")) && args.length > param + 1) {
                                param++;
                                tempFileStore = new File(args[param]);
                        } else {
                                if (inputfile == null)
                                        inputfile = args[param];
                                else if (outputfile == null)
                                        outputfile = args[param];
                                else
                                        System.out.println("Unparsed: "
                                                + args[param]);
                        }
                }
                if (outputfile == null) {
                        System.out
                                .println("please provide input and output file names");
                        displayUsage();
                        return;
                }
                Comparator<String> comparator = defaultcomparator;
                List<File> l = sortInBatch(new File(inputfile), comparator,
                        maxtmpfiles, cs, tempFileStore, distinct, headersize,
                        usegzip);
                if (verbose)
                        System.out
                                .println("created " + l.size() + " tmp files");
                mergeSortedFiles(l, new File(outputfile), comparator, cs,
                        distinct, false, usegzip, null);
        }

        /**
         * This merges several BinaryFileBuffer to an output writer.
         * 
         * @param fbw
         *                A buffer where we write the data.
         * @param cmp
         *                A comparator object that tells us how to sort the
         *                lines.
         * @param distinct
         *                Pass <code>true</code> if duplicate lines should be
         *                discarded. (elchetz@gmail.com)
         * @param buffers
         *                Where the data should be read.
         * @return The number of lines sorted. (P. Beaudoin)
         * @throws IOException
         * 
         */
        public static int mergeSortedFiles(BufferedWriter fbw,
                final Comparator<String> cmp, boolean distinct,
                List<BinaryFileBuffer> buffers) throws IOException {
        	return mergeSortedFiles(fbw, cmp, distinct, buffers, null);
        }
        
        /**
         * This merges several BinaryFileBuffer to an output writer.
         * 
         * @param fbw
         *                A buffer where we write the data.
         * @param cmp
         *                A comparator object that tells us how to sort the
         *                lines.
         * @param distinct
         *                Pass <code>true</code> if duplicate lines should be
         *                discarded. (elchetz@gmail.com)
         * @param buffers
         *                Where the data should be read.
         * @param reducer
         * 				  Converts duplicated lines.                
         *                
         * @return The number of lines sorted. (P. Beaudoin)
         * @throws IOException
         * 
         */
        public static int mergeSortedFiles(BufferedWriter fbw,
                final Comparator<String> cmp, boolean distinct,
                List<BinaryFileBuffer> buffers, Reducer reducer) throws IOException {
        	
        	PriorityQueue<BinaryFileBuffer> pq = new PriorityQueue<BinaryFileBuffer>(
                    11, new Comparator<BinaryFileBuffer>() {
                            @Override
                            public int compare(BinaryFileBuffer i,
                                    BinaryFileBuffer j) {
                                    return cmp.compare(i.peek(), j.peek());
                            }
                    });
            
        	for (BinaryFileBuffer bfb : buffers)
                    if (!bfb.empty())
                            pq.add(bfb);
            
            int rowcounter = 0;
            try {                        
                    if(!distinct) {
                        while (pq.size() > 0) {
                                BinaryFileBuffer bfb = pq.poll();
                                String r = bfb.pop();
                                fbw.write(r);
                                fbw.newLine();
                                ++rowcounter;
                                if (bfb.empty()) {
                                        bfb.fbr.close();
                                } else {
                                        pq.add(bfb); // add it back
                                }
                        }
                    } else {    
                        String lastLine = null;
                        if(pq.size() > 0) {
                 			BinaryFileBuffer bfb = pq.poll();
                 			lastLine = bfb.pop();
                 			fbw.write(lastLine);
                 			fbw.newLine();
                 			++rowcounter;
                 			if (bfb.empty()) {
                 				bfb.fbr.close();
                 			} else {
                 				pq.add(bfb); // add it back
                 			}                			
                 		}
                        while (pq.size() > 0) {
                			BinaryFileBuffer bfb = pq.poll();
                			String r = bfb.pop();
                			// Skip duplicate lines
                			int compare = cmp.compare(r, lastLine);
							if  (compare != 0) {
                				fbw.write(r);
                				fbw.newLine();
                				lastLine = r;
                			}
							else if(reducer != null) {
								String reduced = reducer.merge(lastLine, r);
                				lastLine = reduced != null ? reduced : lastLine;
							}
							
                			++rowcounter;
                			if (bfb.empty()) {
                				bfb.fbr.close();
                			} else {
                				pq.add(bfb); // add it back
                			}
                        }
                    }
            } finally {
                    fbw.close();
                    for (BinaryFileBuffer bfb : pq)
                            bfb.close();
            }
            return rowcounter;


        }

        /**
         * This merges a bunch of temporary flat files
         * 
         * @param files
         *                files to be merged
         * @param outputfile
         *                output file
         * @return The number of lines sorted. (P. Beaudoin)
         * @throws IOException
         */
        public static int mergeSortedFiles(List<File> files, File outputfile)
                throws IOException {
                return mergeSortedFiles(files, outputfile, defaultcomparator,
                        Charset.defaultCharset());
        }

        /**
         * This merges a bunch of temporary flat files
         * 
         * @param files
         * @param outputfile
         * @param cmp
         * @return The number of lines sorted. (P. Beaudoin)
         * @throws IOException
         */
        public static int mergeSortedFiles(List<File> files, File outputfile,
                final Comparator<String> cmp) throws IOException {
                return mergeSortedFiles(files, outputfile, cmp,
                        Charset.defaultCharset());
        }

        /**
         * This merges a bunch of temporary flat files
         * 
         * @param files
         * @param outputfile
         * @param cmp
         * @param distinct
         * @return The number of lines sorted. (P. Beaudoin)
         * @throws IOException
         */
        public static int mergeSortedFiles(List<File> files, File outputfile,
                final Comparator<String> cmp, boolean distinct)
                throws IOException {
                return mergeSortedFiles(files, outputfile, cmp,
                        Charset.defaultCharset(), distinct);
        }

        /**
         * This merges a bunch of temporary flat files
         * 
         * @param files
         * @param outputfile
         * @param cmp
         * @param cs
         *                character set to use to load the strings
         * @return The number of lines sorted. (P. Beaudoin)
         * @throws IOException
         */
        public static int mergeSortedFiles(List<File> files, File outputfile,
                final Comparator<String> cmp, Charset cs) throws IOException {
                return mergeSortedFiles(files, outputfile, cmp, cs, false);
        }

        /**
         * This merges a bunch of temporary flat files
         * 
         * @param files
         *                The {@link List} of sorted {@link File}s to be merged.
         * @param distinct
         *                Pass <code>true</code> if duplicate lines should be
         *                discarded. (elchetz@gmail.com)
         * @param outputfile
         *                The output {@link File} to merge the results to.
         * @param cmp
         *                The {@link Comparator} to use to compare
         *                {@link String}s.
         * @param cs
         *                The {@link Charset} to be used for the byte to
         *                character conversion.
         * @return The number of lines sorted. (P. Beaudoin)
         * @throws IOException
         * @since v0.1.2
         */
        public static int mergeSortedFiles(List<File> files, File outputfile,
                final Comparator<String> cmp, Charset cs, boolean distinct)
                throws IOException {
                return mergeSortedFiles(files, outputfile, cmp, cs, distinct,
                        false, false, null);
        }

        /**
         * This merges a bunch of temporary flat files
         * 
         * @param files
         *                The {@link List} of sorted {@link File}s to be merged.
         * @param distinct
         *                Pass <code>true</code> if duplicate lines should be
         *                discarded. (elchetz@gmail.com)
         * @param outputfile
         *                The output {@link File} to merge the results to.
         * @param cmp
         *                The {@link Comparator} to use to compare
         *                {@link String}s.
         * @param cs
         *                The {@link Charset} to be used for the byte to
         *                character conversion.
         * @param append
         *                Pass <code>true</code> if result should append to
         *                {@link File} instead of overwrite. Default to be false
         *                for overloading methods.
         * @param usegzip
         *                assumes we used gzip compression for temporary files
         * @param header
         * 				  add header line to result file               
         * @return The number of lines sorted. (P. Beaudoin)
         * @throws IOException
         * @since v0.1.4
         */
        public static int mergeSortedFiles(List<File> files, File outputfile,
                final Comparator<String> cmp, Charset cs, boolean distinct,
                boolean append, boolean usegzip, String header) throws IOException {
                
        		ArrayList<BinaryFileBuffer> bfbs = getBFBs(files, cs, usegzip);
                
                BufferedWriter fbw = new BufferedWriter(new OutputStreamWriter(
                        new FileOutputStream(outputfile, append), cs));
                
                if(header != null) {
                	fbw.append(header);
                }
                
                int rowcounter = mergeSortedFiles(fbw, cmp, distinct, bfbs);
                for (File f : files)
                        f.delete();
                return rowcounter;
        }

		private static ArrayList<BinaryFileBuffer> getBFBs(List<File> files,
				Charset cs, boolean usegzip) throws FileNotFoundException,
				IOException {
			
			ArrayList<BinaryFileBuffer> bfbs = new ArrayList<BinaryFileBuffer>();
			for (File f : files) {
			        final int BUFFERSIZE = 2048;
			        InputStream in = new FileInputStream(f);
			        BufferedReader br;
			        if (usegzip) {
			                br = new BufferedReader(
			                        new InputStreamReader(
			                                new GZIPInputStream(in,
			                                        BUFFERSIZE), cs));
			        } else {
			                br = new BufferedReader(new InputStreamReader(
			                        in, cs));
			        }

			        BinaryFileBuffer bfb = new BinaryFileBuffer(br);
			        bfbs.add(bfb);
			}
			return bfbs;
		}

		/**
         * This merges a bunch of temporary flat files
         * 
         * @param files
         *                The {@link List} of sorted {@link File}s to be merged.
         * @param fbw
         *                Result writer               
         * @param distinct
         *                Pass <code>true</code> if duplicate lines should be
         *                discarded. (elchetz@gmail.com)
         * @param cmp
         *                The {@link Comparator} to use to compare
         *                {@link String}s.
         * @param cs
         *                The {@link Charset} to be used for the byte to
         *                character conversion.
         * @param usegzip
         *                assumes we used gzip compression for temporary files
         * @return The number of lines sorted.
         * @throws IOException
         */
		public static int mergeSortedFiles(List<File> files, BufferedWriter fbw,
				final Comparator<String> cmp, Charset cs, boolean distinct,
				boolean usegzip) throws IOException {
			
			return mergeSortedFiles(files, fbw, cmp, cs, distinct, usegzip, null);
					
		}
        
		/**
         * This merges a bunch of temporary flat files
         * 
         * @param files
         *                The {@link List} of sorted {@link File}s to be merged.
         * @param fbw
         *                Result writer               
         * @param distinct
         *                Pass <code>true</code> if duplicate lines should be
         *                discarded. (elchetz@gmail.com)
         * @param cmp
         *                The {@link Comparator} to use to compare
         *                {@link String}s.
         * @param cs
         *                The {@link Charset} to be used for the byte to
         *                character conversion.
         * @param usegzip
         *                assumes we used gzip compression for temporary files
         * @param reducer
         *                interface for merge equal lines               
         * @return The number of lines sorted. (P. Beaudoin)
         * @throws IOException
         */
        public static int mergeSortedFiles(List<File> files, BufferedWriter fbw,
                final Comparator<String> cmp, Charset cs, boolean distinct,
                boolean usegzip, Reducer reducer) throws IOException {
        	
                ArrayList<BinaryFileBuffer> bfbs = getBFBs(files, cs, usegzip);
                
                int rowcounter = mergeSortedFiles(fbw, cmp, distinct, bfbs, reducer);
                
                for (File f : files)
                        f.delete();
                
                return rowcounter;
        }


        /**
         * This sorts a file (input) to an output file (output) using default
         * parameters
         * 
         * @param input
         *                source file
         * 
         * @param output
         *                output file
         * @throws IOException
         */
        public static void sort(final File input, final File output)
                throws IOException {
                ExternalSort.mergeSortedFiles(ExternalSort.sortInBatch(input),
                        output);
        }

        /**
         * Sort a list and save it to a temporary file
         * 
         * @return the file containing the sorted data
         * @param tmplist
         *                data to be sorted
         * @param cmp
         *                string comparator
         * @param cs
         *                charset to use for output (can use
         *                Charset.defaultCharset())
         * @param tmpdirectory
         *                location of the temporary files (set to null for
         *                default location)
         * @throws IOException
         */
        public static File sortAndSave(List<String> tmplist,
                Comparator<String> cmp, Charset cs, File tmpdirectory)
                throws IOException {
                return sortAndSave(tmplist, cmp, cs, tmpdirectory, false, false);
        }

        /**
         * Sort a list and save it to a temporary file
         * 
         * @return the file containing the sorted data
         * @param tmplist
         *                data to be sorted
         * @param cmp
         *                string comparator
         * @param cs
         *                charset to use for output (can use
         *                Charset.defaultCharset())
         * @param tmpdirectory
         *                location of the temporary files (set to null for
         *                default location)
         * @param distinct
         *                Pass <code>true</code> if duplicate lines should be
         *                discarded.
         * @param usegzip
         *                set to true if you are using gzip compression for the
         *                temporary files
         *                
         * @throws IOException
         */
        public static File sortAndSave(List<String> tmplist,
                Comparator<String> cmp, Charset cs, File tmpdirectory,
                boolean distinct, boolean usegzip) throws IOException {
        	return sortAndSave(tmplist, cmp, cs, tmpdirectory, distinct, usegzip, null);
        }
        
        /**
         * Sort a list and save it to a temporary file
         * 
         * @return the file containing the sorted data
         * @param tmplist
         *                data to be sorted
         * @param cmp
         *                string comparator
         * @param cs
         *                charset to use for output (can use
         *                Charset.defaultCharset())
         * @param tmpdirectory
         *                location of the temporary files (set to null for
         *                default location)
         * @param distinct
         *                Pass <code>true</code> if duplicate lines should be
         *                discarded.
         * @param usegzip
         *                set to true if you are using gzip compression for the
         *                temporary files
         *                
         * @param reducer
         * 				  reduces equal lines
         *                
         * @throws IOException
         */
        public static File sortAndSave(List<String> tmplist,
                Comparator<String> cmp, Charset cs, File tmpdirectory,
                boolean distinct, boolean usegzip, Reducer reducer) throws IOException {
        	
        	Collections.sort(tmplist, cmp);// In Java8, we can do tmplist = tmplist.parallelStream().sorted(cmp).collect(Collectors.toCollection(ArrayList<String>::new));
            File newtmpfile = File.createTempFile("sortInBatch",
                    "flatfile", tmpdirectory);
            newtmpfile.deleteOnExit();
            OutputStream out = new FileOutputStream(newtmpfile);
            int ZIPBUFFERSIZE = 2048;
            if (usegzip)
                    out = new GZIPOutputStream(out, ZIPBUFFERSIZE) {
                            {
                                    this.def.setLevel(Deflater.BEST_SPEED);
                            }
                    };
            BufferedWriter fbw = new BufferedWriter(new OutputStreamWriter(
                    out, cs));
            try {
                    if (!distinct) {
                        for (String r : tmplist) {
                                    fbw.write(r);
                                    fbw.newLine();
                        }
                    } else {
                		String lastLine = null;
                		Iterator<String> i = tmplist.iterator();
                		if(i.hasNext()) {
                			lastLine = i.next();
                			fbw.write(lastLine);
              				fbw.newLine();
                		}
                		while (i.hasNext()) {
                			String r = i.next();
                			// Skip duplicate lines
                			if (cmp.compare(r, lastLine) != 0) {
                				fbw.write(r);
                				fbw.newLine();
                				lastLine = r;
                			}
                			else if(reducer != null) {
                				String reduced = reducer.merge(lastLine, r);
                				lastLine = reduced != null ? reduced : lastLine;
                			}
                		}
                    }
            } finally {
                    fbw.close();
            }
            return newtmpfile;
        }

        /**
         * This will simply load the file by blocks of lines, then sort them
         * in-memory, and write the result to temporary files that have to be
         * merged later.
         * 
         * @param fbr
         *                data source
         * @param datalength
         *                estimated data volume (in bytes)
         * @return a list of temporary flat files
         * @throws IOException
         */
        public static List<File> sortInBatch(final BufferedReader fbr,
                final long datalength) throws IOException {
                return sortInBatch(fbr, datalength, defaultcomparator,
                        DEFAULTMAXTEMPFILES, estimateAvailableMemory(),
                        Charset.defaultCharset(), null, false, 0, false);
        }

        /**
         * This will simply load the file by blocks of lines, then sort them
         * in-memory, and write the result to temporary files that have to be
         * merged later.
         * 
         * @param fbr
         *                data source
         * @param datalength
         *                estimated data volume (in bytes)
         * @param cmp
         *                string comparator
         * @param distinct
         *                Pass <code>true</code> if duplicate lines should be
         *                discarded.
         * @return a list of temporary flat files
         * @throws IOException
         */
        public static List<File> sortInBatch(final BufferedReader fbr,
                final long datalength, final Comparator<String> cmp,
                final boolean distinct) throws IOException {
                return sortInBatch(fbr, datalength, cmp, DEFAULTMAXTEMPFILES,
                        estimateAvailableMemory(), Charset.defaultCharset(),
                        null, distinct, 0, false);
        }
        
        /**
         * This will simply load the file by blocks of lines, then sort them
         * in-memory, and write the result to temporary files that have to be
         * merged later.
         * 
         * @param fbr
         *                data source
         * @param datalength
         *                estimated data volume (in bytes)
         * @param cmp
         *                string comparator
         * @param maxtmpfiles
         *                how many tmp files to use
         * @param maxMemory
         *                how many ram to use for one file
         * @param cs
         * 				  CharSet
         * @param tmpdirectory
         *                temp directory for files
         * @param numHeader
         * @param usegzip               
         *                gzip tmp files                                                            
         * @param distinct
         *                Pass <code>true</code> if duplicate lines should be
         *                discarded.
         * @return a list of temporary flat files
         * @throws IOException
         */
        public static List<File> sortInBatch(final BufferedReader fbr,
                final long datalength, final Comparator<String> cmp,
                final int maxtmpfiles, long maxMemory, final Charset cs,
                final File tmpdirectory, final boolean distinct,
                final int numHeader, final boolean usegzip) throws IOException {
        	
        	return sortInBatch(fbr, datalength, cmp, maxtmpfiles, maxMemory, cs,
                    tmpdirectory, distinct, numHeader, usegzip, null);
        }

        /**
         * @param fbr
         *                data source
         * @param datalength
         *                estimated data volume (in bytes)
         * @param cmp
         *                string comparator
         * @param maxtmpfiles
         *                maximal number of temporary files
         * @param maxMemory
         *                maximum amount of memory to use (in bytes)
         * @param cs
         *                character set to use (can use
         *                Charset.defaultCharset())
         * @param tmpdirectory
         *                location of the temporary files (set to null for
         *                default location)
         * @param distinct
         *                Pass <code>true</code> if duplicate lines should be
         *                discarded.
         * @param numHeader
         *                number of lines to preclude before sorting starts
         * @param usegzip
         *                use gzip compression for the temporary files
         * @param reducer
         *                allows to modify lines which was matched as equals
         *                by Comparator cmp                
         * @return a list of temporary flat files
         * @throws IOException
         */
        public static List<File> sortInBatch(final BufferedReader fbr,
                final long datalength, final Comparator<String> cmp,
                final int maxtmpfiles, long maxMemory, final Charset cs,
                final File tmpdirectory, final boolean distinct,
                final int numHeader, final boolean usegzip, Reducer reducer) throws IOException {
                List<File> files = new ArrayList<File>();
                long blocksize = estimateBestSizeOfBlocks(datalength,
                        maxtmpfiles, maxMemory);// in
                // bytes

                try {
                        List<String> tmplist = new ArrayList<String>();
                        String line = "";
                        try {
                                int counter = 0;
                                while (line != null) {
                                        long currentblocksize = 0;// in bytes
                                        while ((currentblocksize < blocksize)
                                                && ((line = fbr.readLine()) != null)) {
                                                // as long as you have enough
                                                // memory
                                                if (counter < numHeader) {
                                                        counter++;
                                                        continue;
                                                }
                                                tmplist.add(line);
                                                currentblocksize += StringSizeEstimator
                                                        .estimatedSizeOf(line);
                                        }
                                        files.add(sortAndSave(tmplist, cmp, cs,
                                                tmpdirectory, distinct, usegzip, reducer));
                                        tmplist.clear();
                                }
                        } catch (EOFException oef) {
                                if (tmplist.size() > 0) {
                                        files.add(sortAndSave(tmplist, cmp, cs,
                                                tmpdirectory, distinct, usegzip, reducer));
                                        tmplist.clear();
                                }
                        }
                } finally {
                        fbr.close();
                }
                return files;
        }

        /**
         * This will simply load the file by blocks of lines, then sort them
         * in-memory, and write the result to temporary files that have to be
         * merged later.
         * 
         * @param file
         *                some flat file
         * @return a list of temporary flat files
         * @throws IOException
         */
        public static List<File> sortInBatch(File file) throws IOException {
                return sortInBatch(file, defaultcomparator,
                        DEFAULTMAXTEMPFILES, Charset.defaultCharset(), null,
                        false);
        }

        /**
         * This will simply load the file by blocks of lines, then sort them
         * in-memory, and write the result to temporary files that have to be
         * merged later.
         * 
         * @param file
         *                some flat file
         * @param cmp
         *                string comparator
         * @return a list of temporary flat files
         * @throws IOException
         */
        public static List<File> sortInBatch(File file, Comparator<String> cmp)
                throws IOException {
                return sortInBatch(file, cmp, DEFAULTMAXTEMPFILES,
                        Charset.defaultCharset(), null, false);
        }

        /**
         * This will simply load the file by blocks of lines, then sort them
         * in-memory, and write the result to temporary files that have to be
         * merged later.
         * 
         * @param file
         *                some flat file
         * @param cmp
         *                string comparator
         * @param distinct
         *                Pass <code>true</code> if duplicate lines should be
         *                discarded.
         * @return a list of temporary flat files
         * @throws IOException
         */
        public static List<File> sortInBatch(File file, Comparator<String> cmp,
                boolean distinct) throws IOException {
                return sortInBatch(file, cmp, DEFAULTMAXTEMPFILES,
                        Charset.defaultCharset(), null, distinct);
        }

        /**
         * This will simply load the file by blocks of lines, then sort them
         * in-memory, and write the result to temporary files that have to be
         * merged later. You can specify a bound on the number of temporary
         * files that will be created.
         * 
         * @param file
         *                some flat file
         * @param cmp
         *                string comparator
         * @param maxtmpfiles
         *                maximal number of temporary files
         * @param cs
         *                character set to use (can use
         *                Charset.defaultCharset())
         * @param tmpdirectory
         *                location of the temporary files (set to null for
         *                default location)
         * @param distinct
         *                Pass <code>true</code> if duplicate lines should be
         *                discarded.
         * @return a list of temporary flat files
         * @throws IOException
         */
        public static List<File> sortInBatch(File file, Comparator<String> cmp,
                int maxtmpfiles, Charset cs, File tmpdirectory, boolean distinct)
                throws IOException {
                return sortInBatch(file, cmp, maxtmpfiles, cs, tmpdirectory,
                        distinct, 0, false);
        }

        /**
         * This will simply load the file by blocks of lines, then sort them
         * in-memory, and write the result to temporary files that have to be
         * merged later. You can specify a bound on the number of temporary
         * files that will be created.
         * 
         * @param file
         *                some flat file
         * @param cmp
         *                string comparator
         * @param maxtmpfiles
         *                maximal number of temporary files
         * @param cs
         *                character set to use (can use
         *                Charset.defaultCharset())
         * @param tmpdirectory
         *                location of the temporary files (set to null for
         *                default location)
         * @param distinct
         *                Pass <code>true</code> if duplicate lines should be
         *                discarded.
         * @param numHeader
         *                number of lines to preclude before sorting starts
         * @param usegzip
         *                use gzip compression for the temporary files
         * @return a list of temporary flat files
         * @throws IOException
         */
        public static List<File> sortInBatch(File file, Comparator<String> cmp,
                int maxtmpfiles, Charset cs, File tmpdirectory,
                boolean distinct, int numHeader, boolean usegzip)
                throws IOException {
                BufferedReader fbr = new BufferedReader(new InputStreamReader(
                        new FileInputStream(file), cs));
                return sortInBatch(fbr, file.length(), cmp, maxtmpfiles,
                        estimateAvailableMemory(), cs, tmpdirectory, distinct,
                        numHeader, usegzip);
        }

        /**
         * default comparator between strings.
         */
        public static Comparator<String> defaultcomparator = new Comparator<String>() {
                @Override
                public int compare(String r1, String r2) {
                	r1 = (null == r1 ? "" : r1);
                	r2 = (null == r2 ? "" : r2);
                	
                    return r1.compareTo(r2);
                }
        };

        /**
         * Default maximal number of temporary files allowed.
         */
        public static final int DEFAULTMAXTEMPFILES = 1024;

}

/**
 * This is essentially a thin wrapper on top of a BufferedReader... which keeps
 * the last line in memory.
 * 
 * @author Daniel Lemire
 */
final class BinaryFileBuffer {
        public BinaryFileBuffer(BufferedReader r) throws IOException {
                this.fbr = r;
                reload();
        }
        public void close() throws IOException {
                this.fbr.close();
        }

        public boolean empty() {
                return this.cache == null;
        }

        public String peek() {
                return this.cache;
        }

        public String pop() throws IOException {
                String answer = peek().toString();// make a copy
                reload();
                return answer;
        }

        private void reload() throws IOException {
                this.cache = this.fbr.readLine();
        }

        public BufferedReader fbr;

        private String cache;

}
