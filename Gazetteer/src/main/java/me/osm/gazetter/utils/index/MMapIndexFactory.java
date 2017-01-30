package me.osm.gazetter.utils.index;

import java.io.File;

public class MMapIndexFactory implements IndexFactory {

	private File tempDir;
	
	public MMapIndexFactory(File tempDir) {
		this.tempDir = tempDir;
	}
	
	@Override
	public BinaryIndex newByteIndex(int rowLength) {
		
		return new MMapBBIndex(rowLength, tempDir);
	}

}
