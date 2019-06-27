package com.cairn.rmi.task;

import com.cairn.common.RDKitOps;
import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.Logger;

import java.util.Map;

/**
 * A task for creating fingerprints from smiles.
 * <p>
 * The setting object should be a hash with String keys:
 * <p>
 * query: boolean value- set true if the smiles is really a query smarts pattern
 * <p>
 * input: value is a single smiles or smarts string
 * <p>
 * The results object contains.
 * <p>
 *
 * @author Gareth Jones
 */
public class FingerprintTask extends AbstractTask {

    private static final long serialVersionUID = 1000L;

    private static final Logger logger = Logger.getLogger(FingerprintTask.class);

    @Override
    public Object submitTask() {

        Map<?, ?> parameters = (Map<?, ?>) settings;

        if (parameters.containsKey("structure1")
                && parameters.containsKey("structure1")) {
            var structure1 = (String) parameters.get("structure1");
            var structure2 = (String) parameters.get("structure2");
            results = RDKitOps.calculateSimilarity(structure1, structure2);
            return results;
        } else {
            boolean query = parameters.containsKey("query") ? (Boolean) parameters.get("query") : false;
            var structure = (String) parameters.get("input");
            results = fingerprintSmiles(structure, query);
            return results;
        }
    }

    /**
     * Creates a fingerprint from a smiles or smarts pattern.
     *
     * @param structure
     * @param query
     * @return
     */
    private String fingerprintSmiles(String structure, boolean query) {
        logger.debug("fingerprintSmiles: query is " + query);
        var molOpt = query ? RDKitOps.smartsToMol(structure): RDKitOps.smilesToMol(structure);
        return molOpt.map(mol -> {
            var fingerprint = RDKitOps.patternFingerPrintMol(mol).toByteArray();
            return Hex.encodeHexString(fingerprint);
        }).orElse("");
    }
}
