package me.osm.osmdoc.read.tagvalueparsers;

import java.util.List;

import me.osm.osmdoc.model.Feature;
import me.osm.osmdoc.model.Tag;
import me.osm.osmdoc.model.Tag.Val;

public interface TagsStatisticCollector {

	public abstract void success(Object pv, Tag tag, Val val, String rawValue,
			TagValueParser parser, List<Feature> poiClassess);

	public abstract void failed(Tag tag, String rawValue, 
			TagValueParser parser, List<Feature> poiClassess);

}