package me.osm.gazetter.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

import me.osm.gazetter.log.GazetteerLogMessage;
import me.osm.gazetter.log.LogWrapperSendListener;

public class BinaryLogsWriter implements LogWrapperSendListener {

	private ObjectOutputStream os;
	
	public BinaryLogsWriter(String logFile) {
		try {
			File file = new File(logFile);
			file.createNewFile();
			os = new ObjectOutputStream(new FileOutputStream(file));
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void sendMsg(GazetteerLogMessage msg) {
		try {
			os.writeObject(msg);
		} catch (IOException e) {
			// Skip broken logging
		}
	}

	public void close() {
		try {
			if(os != null) {
				os.close();
			}
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

}
