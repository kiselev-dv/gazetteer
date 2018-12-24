package me.osm.osmdoc.util;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public final class Strtotime {

    private static final List<Matcher> matchers;

    static {
        matchers = new LinkedList<Matcher>();
        matchers.add(new DateFormatMatcher(new SimpleDateFormat("yyyy-MM-dd")));
        matchers.add(new DateFormatMatcher(new SimpleDateFormat("dd.MM.yyyy")));
        matchers.add(new DateFormatMatcher(new SimpleDateFormat("dd MM yyyy")));
        matchers.add(new DateFormatMatcher(new SimpleDateFormat("yyyyMMdd")));
        
        matchers.add(new DateFormatMatcher(new SimpleDateFormat("MM.yyyy")));
        matchers.add(new DateFormatMatcher(new SimpleDateFormat("MM/yyyy")));
        matchers.add(new DateFormatMatcher(new SimpleDateFormat("yyyy-MM")));
        
        matchers.add(new DateFormatMatcher(new SimpleDateFormat("yyyy")));
        matchers.add(new DateFormatMatcher(new SimpleDateFormat("yyyy's'")));
        // add as many format as you want 
    }

    // not thread-safe
    public static void registerMatcher(Matcher matcher) {
        matchers.add(matcher);
    }

    public static interface Matcher {

        public Date tryConvert(String input);
    }

    private static class DateFormatMatcher implements Matcher {

        private final DateFormat dateFormat;

        public DateFormatMatcher(DateFormat dateFormat) {
            this.dateFormat = dateFormat;
        }

        public Date tryConvert(String input) {
            try {
                return dateFormat.parse(input);
            } catch (ParseException ex) {
                return null;
            }
        }
    }

    public static Date strtotime(String input) {
        for (Matcher matcher : matchers) {
            Date date = matcher.tryConvert(input);

            if (date != null) {
                return date;
            }
        }

        return null;
    }

}