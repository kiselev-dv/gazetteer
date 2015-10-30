package me.osm.gazetter.diff;

import org.joda.time.DateTime;

public final class Counters {
	public long remove = 0;
	public long add = 0;
	public long takeNew = 0;
	public long takeOld = 0;
	
	public int oldHash = 0;
	public DateTime oldTs = new DateTime(0);

	public int newHash = 0;
	public DateTime newTs = new DateTime(0);
}