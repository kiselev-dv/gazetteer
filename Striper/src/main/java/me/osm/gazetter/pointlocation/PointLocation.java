package me.osm.gazetter.pointlocation;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.time.DurationFormatUtils;

import me.osm.gazetter.striper.Constants;

public class PointLocation {
	
	private static final String[] POLYGON_F_TYPES = new String[]{Constants.ADMIN_BOUNDARY_FTYPE, Constants.PLACE_BOUNDARY_FTYPE};

	private static final String[] POINT_F_TYPES = new String[]{Constants.ADDR_POINT_FTYPE};

	private static final ExecutorService executorService = Executors.newFixedThreadPool(4);
	
	private static final AddrPointFormatter addrPointFormatter = new AddrPointFormatter();
	
	public static AtomicInteger counter = new AtomicInteger(); 
	
	public static void main(String[] args) {
		run(args[0], args.length > 1 ? args[1] : null);
	}
	
	private static class StripeFilenameFilter implements FilenameFilter {
		
		@Override
		public boolean accept(File dir, String name) {
			return name.startsWith("stripe");
		}
	
	}
	
	private static final StripeFilenameFilter sfnf = new StripeFilenameFilter();
	

	public static void run(String stripesFolder, String coomonPartFile) {
		
		long start = (new Date()).getTime();
		
		File folder = new File(stripesFolder);
		for(File stripeF : folder.listFiles(sfnf)) {
			executorService.execute(new PLTask(addrPointFormatter, stripeF,	POINT_F_TYPES, POLYGON_F_TYPES));
		}
		
		executorService.shutdown();
		
		try {
			executorService.awaitTermination(1, TimeUnit.HOURS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		System.err.println("Done in " + DurationFormatUtils.formatDurationHMS(new Date().getTime() - start));
	}

}
