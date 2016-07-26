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
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.DisMaxQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
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

	private boolean boostExactName = false;
	
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
		
		BoolQueryBuilder requiredQ = buildRequiredQuery(query, strict, required);
		resultQuery.must(requiredQ);
		
		// Try to find housenumbers using hnSearchReplacers
		Collection<String> housenumbers = fuzzyNumbers(query.woFuzzy().toString());
		context.setHousenumberVariants(housenumbers);
		
		List<QueryBuilder> strictHousenumbers = new ArrayList<>();
		
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
			numberQ.should(QueryBuilders.matchQuery("search", StringUtils.join(housenumbers, " "))
					.boost(WEIGHTS.hnsInSearch));
			
			// if there is more then one number term, boost query over street names.
			// for example in query "City, 8 march, 24" text part should be boosted. 
			if(numbers > 1) {
				int streetNumberMultiplyer = i++ * WEIGHTS.numbersInStreeMul;
				numberQ.should(QueryBuilders.matchQuery("street_name", StringUtils.join(housenumbers, " "))
						.boost(streetNumberMultiplyer));
			}
			
			if(strict) {
				resultQuery.must(numberQ.boost(WEIGHTS.numberQBoost));
			}
			else {
				resultQuery.should(QueryBuilders.boolQuery()
						// boost housenumbers only if we found street and locality
						.must(numberQ.boost(WEIGHTS.numberQBoost))
						.filter(requiredQ));
			}
			
			strictHousenumbers.add(numberQ);
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
					
					strictHousenumbers.add(QueryBuilders.matchQuery("search", token.toString()));
					
					// If there is only one number in query, it must be in matched data
					if (numbers == 1) {
						if(strict) {
							resultQuery.must(QueryBuilders.matchQuery("search", token.toString()))
								.boost(WEIGHTS.numberInHnStrict);
						}
						else {
							resultQuery.should(QueryBuilders.matchQuery("search", token.toString()))
								.boost(WEIGHTS.numberInHnStrict / WEIGHTS.nonStrictHNDebuf);
						}
					}
					else {
						resultQuery.should(QueryBuilders.matchQuery("search", token.toString()))
							.boost(WEIGHTS.numbersInHn / (strict ? 1 : WEIGHTS.nonStrictHNDebuf));
					}
				}
			}
			else if(token.isHasNumbers()) {
				
				strictHousenumbers.add(QueryBuilders.matchQuery("search", token.toString()));
				
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
				nameExact.add(token.toString());
			}
			
			if (token.isHasNumbers()) {
				nums.add(token.toString());
			}
		}

		if(nums.isEmpty()) {
			resultQuery.mustNot(QueryBuilders.termQuery("type", "adrpnt"));
		}
		
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
		
		if(boostExactName) {
			// Boost for exact object name match
			resultQuery.should(QueryBuilders.constantScoreQuery(
					QueryBuilders.boolQuery()
					.must(QueryBuilders.termsQuery("name.exact", exactNameVariants))
					.must(QueryBuilders.termsQuery("type", new String[]{"poipnt", "hghway", "hghnet"}))
					).boost(WEIGHTS.exactName));
		}
		
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
		
		// Если в запросе есть номер дома, выкидываем дома
		// которые не сматчилист по номеру дома 
		if(!strict && !strictHousenumbers.isEmpty()) {
			
			DisMaxQueryBuilder shn = QueryBuilders.disMaxQuery();
			
			for(QueryBuilder qb : strictHousenumbers) {
				shn.add(qb);
			}
			
			resultQuery.mustNot(
					QueryBuilders.boolQuery()
						.must(QueryBuilders.termQuery("type", "adrpnt"))
						.mustNot(shn));
		}
	}

	private BoolQueryBuilder buildRequiredQuery(Query query, boolean strict,
			List<QToken> required) {
		for(QToken token : query.listToken()) {
			if(!token.isNumbersOnly() && !token.isHasNumbers() && !token.isOptional()) {
				// Add regular token to the list of required tokens
				required.add(token);
			}

			if (token.isFuzzied()) {
				// Fuzzied tokens a are required by default 
				required.add(token);
			}
		}
		
		
		BoolQueryBuilder requiredQ = QueryBuilders.boolQuery();
		requiredQ.disableCoord(true);
		
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
		return requiredQ;
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
		
		// Something which looks like locality, should match
		if(required.size() > 1) {
			BoolQueryBuilder localityQ = QueryBuilders.boolQuery();
			localityQ.disableCoord(true);
			localityQ.minimumNumberShouldMatch(1);
			addRequiredTermsNotStrict4Locality(localityQ, fuziedRequieredTerms);
			requiredQ.must(localityQ);
		}
		else {
			addRequiredTermsNotStrict4Locality(requiredQ, fuziedRequieredTerms);
		}
		
		requiredQ.should(QueryBuilders.constantScoreQuery(
				fuzziedQ("street_name", fuziedRequieredTerms, Fuzziness.TWO)).boost(41));
		requiredQ.should(asConstantScore("street_alternate_names", 40, fuziedRequieredTerms));

		requiredQ.should(asConstantScore("nearby_streets.name", 35, fuziedRequieredTerms));
		
		// -----------------------------------------------------
		
		requiredQ.should(asConstantScore("housenumber", 30, fuziedRequieredTerms));

		requiredQ.should(asConstantScore("search", 10, fuziedRequieredTerms));
		requiredQ.should(asConstantScore("name.text", 10, fuziedRequieredTerms));
		
	}

	private void addRequiredTermsNotStrict4Locality(BoolQueryBuilder requiredQ,
			List<String> fuziedRequieredTerms) {
		
		requiredQ.should(QueryBuilders.disMaxQuery()
				.add(asConstantScore("admin0_name", 101, fuziedRequieredTerms))
				.add(asConstantScore("admin0_alternate_names", 100, fuziedRequieredTerms)));

		requiredQ.should(QueryBuilders.disMaxQuery()
				.add(asConstantScore("admin1_name", 91, fuziedRequieredTerms))
				.add(asConstantScore("admin1_alternate_names", 90, fuziedRequieredTerms)));

		requiredQ.should(QueryBuilders.disMaxQuery()
				.add(asConstantScore("admin2_name", 81, fuziedRequieredTerms))
				.add(asConstantScore("admin2_alternate_names", 80, fuziedRequieredTerms)));

		requiredQ.should(QueryBuilders.disMaxQuery()
				.add(asConstantScore("local_admin_name", 71, fuziedRequieredTerms))
				.add(asConstantScore("local_admin_alternate_names", 70, fuziedRequieredTerms)));

		requiredQ.should(QueryBuilders.disMaxQuery()
				.add(fuzziedQ("locality_name", fuziedRequieredTerms, Fuzziness.ONE).boost(61))
				.add(asConstantScore("locality_alternate_names", 60, fuziedRequieredTerms))
				.add(asConstantScore("nearby_places.name", 56, fuziedRequieredTerms)));
		
		requiredQ.should(QueryBuilders.disMaxQuery()
				.add(fuzziedQ("neighborhood_name", fuziedRequieredTerms, Fuzziness.ONE).boost(51))
				.add(asConstantScore("neighborhood_alternate_names", 50, fuziedRequieredTerms))
				.add(asConstantScore("nearest_neighbour.name", 46, fuziedRequieredTerms))
				.add(asConstantScore("nearest_neighbour.alt_names", 46, fuziedRequieredTerms))
				);
	}

	private MatchQueryBuilder fuzziedQ(String field, List<String> fuziedRequieredTerms, Fuzziness f) {
		return QueryBuilders.matchQuery(field, StringUtils.join(fuziedRequieredTerms, " ")).fuzziness(f);
	}

	private ConstantScoreQueryBuilder asConstantScore(String field, float boost,
			List<String> fuziedRequieredTerms) {
		
		return QueryBuilders.constantScoreQuery(
				QueryBuilders.matchQuery(field, StringUtils.join(fuziedRequieredTerms, " "))).boost(boost);
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
