package com.cairn.rmi.task;

import com.cairn.common.RDKitOps;
import org.RDKit.ROMol;
import org.apache.log4j.Logger;

/**
 *
 * @author Gareth Jones
 */
public class MolecularWeightTask extends AbstractTask {

	private static final long serialVersionUID = 1000L;

	private static final Logger logger = Logger.getLogger(MolecularWeightTask.class
			.getName());

	@Override
	public Object submitTask() {
		String smiles = (String) settings;
		logger.debug("processing smiles " + smiles);
		var molOpt = RDKitOps.smilesToMol(smiles);
		var molecularWeight = molOpt.map(RDKitOps::molecularWeight).orElse(Double.NaN);
		molOpt.ifPresent(ROMol::delete);
		logger.debug("got mw " + molecularWeight);

		results = molecularWeight;
		return molecularWeight;
	}
}
