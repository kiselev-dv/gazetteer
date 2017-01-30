package me.osm.gazetter.striper.builders;

import java.nio.ByteBuffer;
import java.util.Comparator;

import me.osm.gazetter.striper.readers.PointsReader.PointsHandler;
import me.osm.gazetter.striper.readers.RelationsReader.RelationsHandler;
import me.osm.gazetter.striper.readers.WaysReader.WaysHandler;

public interface Builder extends RelationsHandler, WaysHandler, PointsHandler {
	
	public static final class FirstLongFieldComparator implements
			Comparator<ByteBuffer> {
		
		@Override
		public int compare(ByteBuffer bb1, ByteBuffer bb2) {
			long l1 = bb1.getLong(0);
			long l2 = bb2.getLong(0);

			if (l1 == l2)
				return 0;

			return l1 > l2 ? 1 : -1;

		}
	}

	public static final class SecondLongFieldComparator implements
			Comparator<ByteBuffer> {
		@Override
		public int compare(ByteBuffer bb1, ByteBuffer bb2) {
			long l1 = bb1.getLong(8);
			long l2 = bb2.getLong(8);

			if (l1 == l2)
				return 0;

			return l1 > l2 ? 1 : -1;
		}
	}

	public static final FirstLongFieldComparator FIRST_LONG_FIELD_COMPARATOR = new FirstLongFieldComparator();
	public static final SecondLongFieldComparator SECOND_LONG_FIELD_COMPARATOR = new SecondLongFieldComparator();
	
	public void secondRunDoneRelations();
	public void secondRunDoneWays();
	public void firstRunDoneNodes();
	public void firstRunDoneWays();
	public void firstRunDoneRelations();
	
	public void close();
	
}