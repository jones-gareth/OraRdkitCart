package com.cairn.rmi.task;

import com.cairn.common.RDKitOps;
import org.RDKit.RDKFuncs;
import org.apache.log4j.Logger;


/**
 *
 * @author Gareth Jones
 */
public class ExactMassTask extends AbstractTask {

    private static final long serialVersionUID = 1000L;

    private static final Logger logger = Logger.getLogger(MolecularWeightTask.class
            .getName());

    @Override
    public Object submitTask() {
        String smiles = (String) settings;
        logger.debug("processing smiles " + smiles);
        var molOpt = RDKitOps.smilesToMol(smiles);
        var exactMass = molOpt.map(RDKFuncs::calcExactMW).orElse(Double.NaN);
        results = exactMass;
        return exactMass;
    }

}
