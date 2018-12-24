package me.osm.osmdoc.read.tagvalueparsers;

import java.text.SimpleDateFormat;
import java.util.Date;

import me.osm.osmdoc.util.Strtotime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DateParser implements TagValueParser {
	
	public static class FormattedDate extends Date {
		
		private static final long serialVersionUID = -3625294105328787993L;
		
		private static final SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
		
		public FormattedDate(Date date) {
			super(date.getTime());
		}
		
		@Override
		public String toString() {
			return f.format(this);
		}
	}

	Logger log = LoggerFactory.getLogger(DateParser.class);
	
	@Override
	public Object parse(String rawValue) {
		
		Date date = Strtotime.strtotime(rawValue);
		if(date == null) {
			log.warn("Failed to parse date: {}", rawValue);
			return null;
		}
		
		return new FormattedDate(date);
	}

}
