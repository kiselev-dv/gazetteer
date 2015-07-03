package me.osm.gazetteer.web.api.search;

import java.util.List;

import me.osm.gazetteer.web.api.query.QToken;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.FuzzyQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

public class SearchBuilderOld extends SearchBuilderImpl {
	
	@Override
	protected void addTermsNotStrict(List<QToken> required,
			BoolQueryBuilder requiredQ) {
		
		for(QToken t : required) {
			String term = t.toString();
			
			if(t.isFuzzied()) {
				term = StringUtils.join(t.getVariants(), ' ');
			}
			
			// In not strict variant term must appears in search field or in name of nearby street
			// Also add fuzzines
			QueryBuilder search = QueryBuilders.fuzzyQuery("search", term).boost(20);
			QueryBuilder nearestN = QueryBuilders.matchQuery("nearest_neighbour.name", term).boost(10);
			QueryBuilder nearestS = QueryBuilders.matchQuery("nearby_streets.name", term).boost(0.2f);
			
			if(!t.isFuzzied()) {
				// If term wasn't fuzzied duiring analyze, add fuzzyness
				((FuzzyQueryBuilder) search).fuzziness(Fuzziness.ONE);
			}
			
			requiredQ.should(QueryBuilders.disMaxQuery().tieBreaker(0.4f)
					.add(search)
					.add(nearestS)
					.add(nearestN));
		}
		
	}
	
}
