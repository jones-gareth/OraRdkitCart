package com.cairn.rmi.task;

import com.cairn.common.CommonUtils;
import com.cairn.common.SubstructureMatcher;
import com.cairn.rmi.TaskException;
import com.cairn.common.RDKitOps;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.util.Map;

/**
 * A task to handle functional operators (i.e. search not on domain indexes).
 * 
 * Available operations are substructure, similarity and exact_match.
 * 
 * @author Gareth Jones
 *
 */
public class FunctionalOperatorTask extends AbstractTask {

	private static final long serialVersionUID = 1000L;

	private static final Logger logger = Logger.getLogger(FunctionalOperatorTask.class);

	@Override
	public Object submitTask() throws TaskException {

		Map<?, ?> parameters = (Map<?, ?>) settings;
		String query = (String) parameters.get("query");
		String target = (String) parameters.get("target");

		results = false;

		String operation = (String) parameters.get("operation");

		try {

			switch (operation) {
				case "substructure": {
					var queryTypeString = (String) parameters.get("query_type");
					var queryType = SubstructureMatcher.SubSearchQueryType.fromString(queryTypeString);


					logger.debug("Functional substructure search of " + query + " against "
							+ target + "[ query type : " + queryType + "]");

					var matcher = new SubstructureMatcher(queryType, query);
					var molOpt = RDKitOps.smilesToMol(target, false);
					var match = molOpt.map(mol -> {
						var m = matcher.matchStructure(mol, null);
						mol.delete();
						return m;
					});
					results = match;
					return match;

				}
				case "similarity": {

					double minSimilarity = (Double) parameters.get("min_similarity");
					logger.debug("Functional similarity search of " + query + " against "
							+ target + "[ minSimilarity : " + minSimilarity + "]");

					var similarity = RDKitOps.calculateSimilarity(query, target);
					boolean match = similarity >= minSimilarity;

					logger.debug("match: " + match + "[ similarity " + similarity + " ]");
					results = match;
					return match;
				}
				case "exact_match": {

					logger.debug("Functional exact match search of " + query + " against "
							+ target);
					var querySmiles = RDKitOps.canonicalize(query).orElse(null);
					var targetSmiles = RDKitOps.canonicalize(target).orElse(null);
					var match = false;
					if (querySmiles != null && targetSmiles != null)
						match = querySmiles.equals(targetSmiles);

					logger.debug("match: " + match);
					results = match;
					return match;
				}
			}


		} catch (Exception e) {
			throw new TaskException("Exception" + CommonUtils.getStackTrace(e));
		}

		throw new TaskException("Unknown operation " + operation);
	}

}
