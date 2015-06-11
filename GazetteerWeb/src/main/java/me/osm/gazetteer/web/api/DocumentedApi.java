package me.osm.gazetteer.web.api;

import me.osm.gazetteer.web.api.meta.Endpoint;

import org.restexpress.domain.metadata.UriMetadata;

public interface DocumentedApi {

	/**
	 * Creates API metainfo.
	 * */
	public Endpoint getMeta(UriMetadata uriMetadata);

}
