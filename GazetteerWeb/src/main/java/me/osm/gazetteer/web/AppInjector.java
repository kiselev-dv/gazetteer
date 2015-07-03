package me.osm.gazetteer.web;

import me.osm.gazetteer.web.api.query.QueryAnalyzer;
import me.osm.gazetteer.web.api.query.QueryAnalyzerImpl;
import me.osm.gazetteer.web.api.search.SearchBuilder;
import me.osm.gazetteer.web.api.search.SearchBuilderImpl;
import me.osm.gazetteer.web.api.utils.Paginator;
import me.osm.gazetteer.web.api.utils.PaginatorImpl;

import com.google.inject.AbstractModule;

public class AppInjector extends AbstractModule {

	@Override
	protected void configure() {
		bind(QueryAnalyzer.class).to(QueryAnalyzerImpl.class);
		bind(Paginator.class).to(PaginatorImpl.class);
		bind(SearchBuilder.class).to(SearchBuilderImpl.class);
	}

}
