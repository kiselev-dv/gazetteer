package me.osm.gazetteer.web.api.search;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

import me.osm.gazetteer.web.api.query.QToken;
import me.osm.gazetteer.web.api.query.Query;
import me.osm.gazetteer.web.api.utils.BuildSearchQContext;
import me.osm.gazetteer.web.imp.Replacer;
import me.osm.gazetteer.web.utils.ReplacersCompiler;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.DisMaxQueryBuilder;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Singleton;

@Singleton
public class SearchBuilderImpl implements SearchBuilder {
	
	/**
	 * Search and fuzzy housenumbers
	 * */
	protected List<Replacer> housenumberReplacers = new ArrayList<>();
	
	private Weights WEIGHTS;
	
	private static final Logger log = LoggerFactory.getLogger(SearchBuilderImpl.class);
	
	public SearchBuilderImpl() {
		ReplacersCompiler.compile(housenumberReplacers, new File("config/replacers/search/hnSearchReplacers"));
		WEIGHTS = Weights.readFromFile();
	}

	/* (non-Javadoc)
	 * @see me.osm.gazetteer.web.api.SearchBuilder#mainSearchQ(me.osm.gazetteer.web.api.query.Query, org.elasticsearch.index.query.BoolQueryBuilder, boolean, me.osm.gazetteer.web.api.utils.BuildSearchQContext)
	 */
	@Override
	public void mainSearchQ(Query query, BoolQueryBuilder resultQuery, boolean strict, BuildSearchQContext context) {
		
		int numbers = query.countNumeric();
		
		List<String> nameExact = new ArrayList<String>();
		List<QToken> required = new ArrayList<QToken>();
		LinkedHashSet<String> nums = new LinkedHashSet<String>();
		
		// Try to find housenumbers using hnSearchReplacers
		Collection<String> housenumbers = fuzzyNumbers(query.woFuzzy().toString());
		context.setHousenumberVariants(housenumbers);
		
		// If those numbers were found
		// Add those numbers into subquery
		// Longer variants will have score boost
		if(!housenumbers.isEmpty()) {
			
			BoolQueryBuilder numberQ = QueryBuilders.boolQuery();
			
			//TODO: Change weighting. Move requested form first (last after reverse).
			List<String> reversed = new ArrayList<>(housenumbers);
			Collections.sort(reversed, new Comparator<String>() {

				@Override
				public int compare(String o1, String o2) {
					return Integer.compare(StringUtils.length(o1), StringUtils.length(o2));
				}
				
			});
			Collections.reverse(reversed);
			
			int i = 1;
			for(String variant : reversed) {
				numberQ.should(QueryBuilders.termQuery("housenumber", variant).boost(i++ * WEIGHTS.hnVariansStep)); 
			}
			numberQ.should(QueryBuilders.matchQuery("search", housenumbers).boost(WEIGHTS.hnsInSearch));
			
			// if there is more then one number term, boost query over street names.
			// for example in query "City, 8 march, 24" text part should be boosted. 
			if(numbers > 1) {
				int streetNumberMultiplyer = i++ * WEIGHTS.numbersInStreeMul;
				numberQ.should(QueryBuilders.matchQuery("street_name", housenumbers).boost(streetNumberMultiplyer));
			}
			
			if(strict) {
				resultQuery.must(numberQ.boost(WEIGHTS.numberQBoost));
			}
			else {
				resultQuery.should(numberQ.boost(WEIGHTS.numberQBoost / WEIGHTS.nonStrictHNDebuf));
			}
		}
		
		for(QToken token : query.listToken()) {
	
			if(token.isOptional()) {
				
				MultiMatchQueryBuilder option = 
						QueryBuilders.multiMatchQuery(token.toString(), "search", "nearest_neighbour.name");
				
				//Optional but may be important
				if(token.toString().length() > 3) {
					option.boost(WEIGHTS.optionalTermBoost);
				}
				
				resultQuery.should(option);
			}
			else if(token.isNumbersOnly()) {
				
				// Если реплейсеры НЕ распознали номер дома
				// If hnSearch replacers fails to find any housenumbers
				if(housenumbers.isEmpty()) {
					
					// If there is only one number in query, it must be in matched data
					if (numbers == 1) {
						if(strict) {
							resultQuery.must(QueryBuilders.matchQuery("search", token.toString()))
								.boost(WEIGHTS.numberInHnStrict);
						}
						else {
							resultQuery.should(QueryBuilders.matchQuery("search", token.toString()))
								.boost(WEIGHTS.numberInHnStrict/ WEIGHTS.nonStrictHNDebuf);
						}
					}
					else {
						resultQuery.should(QueryBuilders.matchQuery("search", token.toString()))
							.boost(WEIGHTS.numbersInHn/ (strict ? 1 : WEIGHTS.nonStrictHNDebuf));
					}
				}
			}
			else if(token.isHasNumbers()) {
				// Если реплейсеры не распознали номер дома, то пробуем действовать по старинке.
				// If hnSearch replacers fails to find any housenumbers
				if(housenumbers.isEmpty()) {
					BoolQueryBuilder numberQ = QueryBuilders.boolQuery();
					numberQ.disableCoord(true);
					
					//for numbers in street names
					numberQ.should(QueryBuilders.matchQuery("search", token.toString()));
					
					numberQ.should(QueryBuilders.termQuery("housenumber", token.toString())
							.boost(WEIGHTS.hasNumbersInHn / (strict ? 1 : WEIGHTS.nonStrictHNDebuf)));
					
					nums.add(token.toString());
					
					if(strict) {
						resultQuery.must(numberQ);
					}
					else {
						resultQuery.should(numberQ);
					}
					nameExact.add(token.toString());
				}
			}
			else if(!token.isFuzzied()) {
				// Add regular token to the list of required tokens
				required.add(token);
				nameExact.add(token.toString());
			}
			
			if (token.isHasNumbers()) {
				nums.add(token.toString());
			}
			
			if (token.isFuzzied()) {
				// Fuzzied tokens a are required by default 
				required.add(token);
			}
		}
		
		BoolQueryBuilder requiredQ = QueryBuilders.boolQuery();
		requiredQ.disableCoord(true);
		
		if(nums.isEmpty()) {
			resultQuery.mustNot(QueryBuilders.termQuery("type", "adrpnt"));
		}
		
		if(strict) {
			addTermsStrict(required, requiredQ);
		}
		else {
			addTermsNotStrict(required, requiredQ);
		}

		int requiredCount = required.size();
		
		if(strict) {
			// In strict variant all terms must be in search field
			requiredQ.minimumNumberShouldMatch(requiredCount);
		}
		else {
			//TODO: Move to weights
			if(requiredCount > 3) {
				requiredQ.minimumNumberShouldMatch(requiredCount - 2);
			}
			else if(requiredCount >= 2) {
				requiredQ.minimumNumberShouldMatch(requiredCount - 1);
			}
		}
		
		resultQuery.must(requiredQ);
		
		log.debug("Request{}: {} Required tokens: {} Housenumbers variants: {}", 
				new Object[]{strict ? " (strict)" : "", query.print(), required, housenumbers});
		
		List<String> exactNameVariants = new ArrayList<String>();
		for(String s : nameExact) {
			// Original term from query analyzer (lowercased)
			exactNameVariants.add(s);
			
			// Camel Case
			exactNameVariants.add(StringUtils.capitalize(s));

			// UPPER CASE
			exactNameVariants.add(StringUtils.upperCase(s));
			
			exactNameVariants.addAll(query.getOriginalVarians());
		}
		
		// Boost for exact object name match
		resultQuery.should(QueryBuilders.termsQuery("name.exact", exactNameVariants)
				.boost(WEIGHTS.exactName));
		
		// Boost for house number match
		if(strict) {
			resultQuery.should(QueryBuilders.termsQuery("housenumber", nums)
					.boost(WEIGHTS.numbersInHnOpt));
		}
		else {
			resultQuery.should(QueryBuilders.termsQuery("housenumber", nums)
					.boost(WEIGHTS.numbersInHnOpt / WEIGHTS.nonStrictHNDebuf));
		}
		
		resultQuery.disableCoord(true);
		resultQuery.mustNot(QueryBuilders.termQuery("weight", 0));
	}

	/**
	 * Fill data for <b>non strict</b> case
	 * */
	protected void addTermsNotStrict(List<QToken> required,
			BoolQueryBuilder requiredQ) {
		
		List<String> fuziedRequieredTerms = new ArrayList<>();
		
		for(QToken t : required) {
			if(t.isFuzzied()) {
				fuziedRequieredTerms.addAll(t.getVariants());
			}

			fuziedRequieredTerms.add(t.toString());
		}
		
		requiredQ.disableCoord(true);
		
		requiredQ.should(QueryBuilders.matchQuery("admin0_name", fuziedRequieredTerms).boost(101));
		requiredQ.should(QueryBuilders.matchQuery("admin0_alternate_names", fuziedRequieredTerms).boost(100));
		
		requiredQ.should(QueryBuilders.matchQuery("admin1_name", fuziedRequieredTerms).boost(91));
		requiredQ.should(QueryBuilders.matchQuery("admin1_alternate_names", fuziedRequieredTerms).boost(90));

		requiredQ.should(QueryBuilders.matchQuery("admin2_name", fuziedRequieredTerms).boost(81));
		requiredQ.should(QueryBuilders.matchQuery("admin2_alternate_names", fuziedRequieredTerms).boost(80));

		requiredQ.should(QueryBuilders.matchQuery("local_admin_name", fuziedRequieredTerms).boost(71));
		requiredQ.should(QueryBuilders.matchQuery("local_admin_alternate_names", fuziedRequieredTerms).boost(70));
		
		requiredQ.should(QueryBuilders.matchQuery("locality_name", fuziedRequieredTerms).boost(61).fuzziness(Fuzziness.ONE));
		requiredQ.should(QueryBuilders.matchQuery("locality_alternate_names", fuziedRequieredTerms).boost(60));

		requiredQ.should(QueryBuilders.matchQuery("nearby_places.name", fuziedRequieredTerms).boost(56));
		
		requiredQ.should(QueryBuilders.matchQuery("neighborhood_name", fuziedRequieredTerms).boost(51).fuzziness(Fuzziness.ONE));
		requiredQ.should(QueryBuilders.matchQuery("neighborhood_alternate_names", fuziedRequieredTerms).boost(50));

		requiredQ.should(QueryBuilders.matchQuery("nearest_neighbour.name", fuziedRequieredTerms).boost(46));
		requiredQ.should(QueryBuilders.matchQuery("nearest_neighbour.alt_names", fuziedRequieredTerms).boost(46));
		
		requiredQ.should(QueryBuilders.matchQuery("street_name", fuziedRequieredTerms).boost(41).fuzziness(Fuzziness.TWO));
		requiredQ.should(QueryBuilders.matchQuery("street_alternate_names", fuziedRequieredTerms).boost(40));

		requiredQ.should(QueryBuilders.matchQuery("nearby_streets.name", fuziedRequieredTerms).boost(35));
		
		requiredQ.should(QueryBuilders.matchQuery("housenumber", fuziedRequieredTerms).boost(30));

		requiredQ.should(QueryBuilders.matchQuery("search", fuziedRequieredTerms).boost(10));
		
	}

	/**
	 * Fill data for <b>strict</b> case
	 * */
	protected void addTermsStrict(List<QToken> required,
			BoolQueryBuilder requiredQ) {
		
		for(QToken t : required) {
			if(t.isFuzzied()) {
				// In strict version one of the term variants must appears in search field
				DisMaxQueryBuilder variantsQ = QueryBuilders.disMaxQuery().boost(20);
				requiredQ.should(variantsQ);
				
				for(String s : t.getVariants()) {
					variantsQ.add(QueryBuilders.matchQuery("search", s).boost(1));
					variantsQ.add(QueryBuilders.matchQuery("street_name", s).boost(10));
					variantsQ.add(QueryBuilders.matchQuery("nearest_neighbour.name", s).boost(5));
				}
			}
			else {
				// In strict version term must appear in document's search field
				requiredQ.should(QueryBuilders.matchQuery("search", t.toString()).boost(20));
			}
		}
		
	}

	/**
	 * Generates housenumbers variants
	 * 
	 * @param hn housenumber part of query or full query
	 * */
	private Collection<String> fuzzyNumbers(String hn) {

		List<String> result = new ArrayList<>();
		
		if(StringUtils.isNotBlank(hn)) {
			LinkedHashSet<String> tr = transformHousenumbers(hn);
			result.addAll(tr);
		}
		
		return result;
	}

	/**
	 * Generates housenumbers variants using housenumberReplacers
	 * see hnSearchReplacers
	 * 
	 * @param optString housenumber part of query or full query
	 * */
	private LinkedHashSet<String> transformHousenumbers(String optString) {
		LinkedHashSet<String> result = new LinkedHashSet<>(); 
		for(Replacer replacer : housenumberReplacers) {
			try {
				Collection<String> replace = replacer.replace(optString);
				if(replace != null) {
					result.addAll(replace);
				}
			}
			catch (Exception e) {
				LoggerFactory.getLogger(getClass()).warn("Exception in Replacer", e);
			}
		}
		
		return result;
	}

}
