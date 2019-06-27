package com.cairn.rmi.task;


import com.cairn.common.MoleculeCache;
import org.apache.log4j.Logger;

/**
 * @author Gareth Jones
 * <p>
 * A simple task to add compounds to the molecule cache.
 */
public class MoleculeCacheTask extends AbstractTask {

    private static final long serialVersionUID = 1000L;

    private static final Logger logger = Logger.getLogger(MoleculeCacheTask.class);

    @Override
    public Object submitTask() {
        logger.debug("Starting MoleculeCacheTask");

        var smiles = (String[]) settings;
        if (smiles.length == 1) {
            // the cache on and off commands are designed for use by the test suite.
            var command = smiles[0];
            if (command.equalsIgnoreCase("cacheOn")) {
                logger.debug("Enabling molecule cache");
                MoleculeCache.setUseMoleculeCache(true);
                return true;
            } else if (command.equalsIgnoreCase("cacheOff")) {
                logger.debug("Disabling molecule cache");
                MoleculeCache.setUseMoleculeCache(false);
                return true;
            }
        }

        if (!MoleculeCache.isUseMoleculeCache()) {
            logger.info("Structure search caching is not enabled!");
            return false;
        }
        int nSmiles = 0;
        MoleculeCache moleculeCache = MoleculeCache.getMoleculeCache();
        for (String smilesString : smiles) {
            if (smilesString == null)
                continue;
            logger.debug("Adding smiles " + smilesString);
            moleculeCache.useMolecule((mol) -> {
            }, smilesString);
            nSmiles++;
        }

        logger.info("Added " + nSmiles + " molecules to cache");
        moleculeCache.info();
        return true;
    }

}
