package me.osm.gazetter.join.util;

import java.io.File;
import java.util.List;

public interface JoinFailuresHandler {
	public void failed(List<File> f);
}
