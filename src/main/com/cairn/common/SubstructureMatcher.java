package com.cairn.common;

import org.RDKit.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.log4j.Logger;

import java.util.BitSet;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * A class to facilitate substructure search using RDKit
 *
 * @author Gareth Jones
 */
public class SubstructureMatcher {

    // Substructure search query types
    public enum SubSearchQueryType {
        SMARTS, MDL;

        public static SubSearchQueryType fromString(String stringQueryType) {
            if (stringQueryType.equalsIgnoreCase("smarts"))
                return SubSearchQueryType.SMARTS;
            else if (stringQueryType.equalsIgnoreCase("mdl")) {
                return SubSearchQueryType.MDL;
            } else {
                String message = "Unknown substructure search type " + stringQueryType;
                logger.error(message);
                throw new IllegalArgumentException(message);
            }
        }
    }

    private final SubSearchQueryType queryType;
    private final String queryString;

    private final ROMol queryMol;
    private final BitSet queryFingerprint;
    private final int[] queryOnBits;

    private final SubstructMatchParameters matchParameters;

    private final AtomicInteger nHits = new AtomicInteger(0);
    private final AtomicInteger screenOut = new AtomicInteger(0);
    private final AtomicInteger nProcessed = new AtomicInteger(0);
    private final AtomicBoolean failingOnTrustedSmiles = new AtomicBoolean(false);

    private final ThreadLocal<ROMol> threadQuery;


    private static final Logger logger = Logger.getLogger(SubstructureMatcher.class);

    public SubstructureMatcher(SubSearchQueryType queryType, String queryString) {
        this(queryType, queryString, true, true, false, 2048);
    }

    public SubstructureMatcher(SubSearchQueryType queryType, String queryString, int fingerprintSize) {
        this(queryType, queryString, true, true, false, fingerprintSize);
    }

    /**
     * Creates the query objects from MDL or smarts pattern
     *
     * @param queryType
     * @param queryString
     * @param useChirality
     * @param recursionPossible
     * @param queryQueryMatches
     */
    public SubstructureMatcher(SubSearchQueryType queryType, String queryString, boolean useChirality, boolean recursionPossible, boolean queryQueryMatches, int fingerprintSize) {

        if (StringUtils.isEmpty(queryString))
            throw new IllegalArgumentException(
                    "No query specified");

        this.queryType = queryType;
        this.queryString = queryString;


        Optional<ROMol> queryMolOpt;
        switch (queryType) {
            case MDL:
                queryString = queryString.replace('|', '\n');
                queryMolOpt = RDKitOps.sdfToMol(queryString);
                break;
            case SMARTS:
                queryMolOpt = RDKitOps.smartsToMol(queryString);
                break;
            default:
                throw new IllegalArgumentException();
        }

        if (queryMolOpt.isEmpty())
            throw new IllegalArgumentException("Unable to parse query " + queryString);

        var queryMol = queryMolOpt.get();
        logger.debug(RDKitOps.molInfo(queryMol));
        this.queryMol = queryMol;

        matchParameters = new SubstructMatchParameters();
        matchParameters.setRecursionPossible(recursionPossible);
        matchParameters.setUseQueryQueryMatches(queryQueryMatches);
        matchParameters.setUseChirality(useChirality);

        var fingerprint = RDKitOps.patternFingerPrintMol(queryMol, fingerprintSize);
        this.queryFingerprint = RDKitOps.explictBitVectToBitSet(fingerprint);
        this.queryOnBits = IntStream.range(0, fingerprintSize).filter(queryFingerprint::get).toArray();
        this.threadQuery = ThreadLocal.withInitial(() -> new ROMol(queryMol));
    }

    /**
     * Match a molecule against the query structure.
     *
     * @param mol
     * @param targetFingerprint
     * @return
     */
    public boolean matchStructure(ROMol mol, BitSet targetFingerprint) {
        nProcessed.incrementAndGet();

        if (targetFingerprint != null) {
            assert targetFingerprint.size() == queryFingerprint.size();
            if (!matchTargetFingerprint(targetFingerprint))
                return false;
        }
        screenOut.incrementAndGet();

        // uncomment this line if the query molecule should not be shared between threads
        //var match = mol.hasSubstructMatch(threadQuery.get(), matchParameters);
        var match = mol.hasSubstructMatch(queryMol, matchParameters);

        if (match)
            nHits.incrementAndGet();
        return match;
    }

    private boolean matchTargetFingerprint(BitSet targetFingerprint) {
        for (var on : queryOnBits) {
            if (!targetFingerprint.get(on))
                return false;
        }
        return true;
    }

    /**
     * Match a smiles against the query.
     *
     * @param targetSmiles
     * @param trusted
     * @param targetFingerprint
     * @return
     */
    public boolean matchStructure(String targetSmiles, boolean trusted, BitSet targetFingerprint) {
        nProcessed.incrementAndGet();
        if (targetFingerprint != null) {
            assert targetFingerprint.size() == queryFingerprint.size();
            if (!matchTargetFingerprint(targetFingerprint)) {
                return false;
            }
        }

        boolean isHit;
        if (MoleculeCache.isUseMoleculeCache()) {
            screenOut.incrementAndGet();
            final MutableBoolean match = new MutableBoolean(false);
            MoleculeCache.UseCacheMolecule useCacheMolecule = (mol) -> {
                if (mol != null) {
                    if (failingOnTrustedSmiles.get()) {
                        boolean m = matchMol(targetSmiles, false);
                        match.setValue(m);
                    } else {
                        try {
                            boolean m = mol.hasSubstructMatch(queryMol, matchParameters);
                            match.setValue(m);
                        } catch (GenericRDKitException ex) {
                            if (trusted)
                                failingOnTrustedSmiles.set(true);
                            logger.warn("GenericRDKitException matching target " + targetSmiles + " to query " + RDKFuncs.MolToSmarts(queryMol));
                            boolean m = matchMol(targetSmiles, false);
                            match.setValue(m);
                        }
                    }
                }
            };
            MoleculeCache.getMoleculeCache().useMolecule(useCacheMolecule, targetSmiles, trusted);
            isHit = match.booleanValue();
            if (isHit) nHits.incrementAndGet();
        } else {
            if (failingOnTrustedSmiles.get()) {
                isHit = matchMol(targetSmiles, false);
            } else {
                try {
                    isHit = matchMol(targetSmiles, trusted);
                } catch (GenericRDKitException ex) {
                    // For some queries e.g. "[#7;D3](*=*)(-&!@*)*:*" the molecule created from the trusted smile will fail,
                    // so redo search on fully sanitized molecule.
                    logger.warn("GenericRDKitException matching target " + targetSmiles + " to query " + RDKFuncs.MolToSmarts(queryMol));
                    if (trusted) {
                        failingOnTrustedSmiles.set(true);
                        isHit = matchMol(targetSmiles, false);
                    } else
                        isHit = false;
                }
            }
            nProcessed.decrementAndGet();
        }

        return isHit;
    }

    private boolean matchMol(String targetSmiles, boolean trusted) {
        var molOpt = RDKitOps.smilesToMol(targetSmiles, trusted);
        return molOpt.map(mol -> {
            var m = matchStructure(mol, null);
            mol.delete();
            return m;
        }).orElse(false);
    }

    /**
     * Free search memory
     */
    public synchronized void free() {
    }

    public int getnHits() {
        return nHits.get();
    }

    public int getScreenOut() {
        return screenOut.get();
    }

    public int getnProcessed() {
        return nProcessed.get();
    }

    /**
     * @return the queryMol
     */
    public ROMol getQueryMol() {
        return queryMol;
    }

}
