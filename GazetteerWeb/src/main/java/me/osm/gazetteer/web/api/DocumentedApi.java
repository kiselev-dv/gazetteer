package me.osm.gazetteer.web.api;

import org.restexpress.domain.metadata.UriMetadata;

import me.osm.gazetteer.web.api.meta.Endpoint;

public interface DocumentedApi {

	/**
	 * Creates API metainfo.
	 * */
	public Endpoint getMeta(UriMetadata uriMetadata);

}
