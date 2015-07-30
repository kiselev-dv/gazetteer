package me.osm.gazetter.log.messages;

import java.io.File;
import java.util.List;

import me.osm.gazetter.log.GazetteerLogMessage;
import me.osm.gazetter.log.LogLevel;
import me.osm.gazetter.log.LogLevel.Level;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

public class FailedToJoinMessage extends GazetteerLogMessage {

	private String[] fails;
	
	public FailedToJoinMessage(List<File> fails) {
		this.fails = new String[fails.size()];
		int i = 0;
		for(File f : fails) {
			this.fails[i++] = f.getName();
		}
	}

	private static final long serialVersionUID = -6251136997068722887L;

	@Override
	public void log(Logger root, Level level) {
		LogLevel.log(root, level, "Failed to join {}", StringUtils.join(fails));
	}


}
