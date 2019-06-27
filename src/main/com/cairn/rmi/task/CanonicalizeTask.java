package com.cairn.rmi.task;

import com.cairn.common.RDKitOps;
import org.RDKit.Int_Vect;
import org.apache.log4j.Logger;

/**
 * A class to canonicalize smiles.
 *
 * @author Gareth Jones
 * 
 */
public class CanonicalizeTask extends AbstractTask {

	private static final long serialVersionUID = 1000L;


	@Override
	public Object submitTask() {
		String smilesIn = (String) settings;
		var canSmiles = RDKitOps.canonicalize(smilesIn).orElse(null);
		results = canSmiles;
		new Int_Vect();
		return canSmiles;
	}

}
