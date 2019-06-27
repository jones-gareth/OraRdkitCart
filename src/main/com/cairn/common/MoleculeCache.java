package com.cairn.common;

import org.RDKit.ROMol;
import org.apache.commons.collections4.map.LRUMap;
import org.apache.log4j.Logger;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Uses the commons collection LRU map to cache molecules.
 * To check out a molecule use getMolecule. Once you are done use finished to
 * release the lock on that structure.
 *
 * @author Gareth Jones
 */
public class MoleculeCache {
    // set true to cache molecules
    private static boolean useMoleculeCache = false;
    // set true to allow concurrent searches on target molecules
    // both settings work for RDKit molecules
    private static final boolean ALLOW_CONCURRENT_TARGET_ACCESS = true;

    static private volatile MoleculeCache moleculeCache;
    private static final Logger logger = Logger.getLogger(MoleculeCache.class.getName());

    private final Map<String, ROMol> molecules;

    private final AtomicInteger nHits=new AtomicInteger(0), nPuts=new AtomicInteger(0), nRemovals=new AtomicInteger(0);
    private final Map<String, ReentrantLock> structureLockMap = new HashMap<>();

    private final Map<String, Integer> moleculeUseCount = new HashMap<>();


    @SuppressWarnings("unchecked")
    private MoleculeCache(int cacheSize) {
        // note commons collections are untyped
        logger.debug("creating cache");
        info();
        MoleculeLRUMap lrumap = new MoleculeLRUMap(cacheSize);
        molecules = Collections.synchronizedMap(lrumap);
    }

    public static void createMoleculeCache(int cacheSize) {
        moleculeCache = new MoleculeCache(cacheSize);
    }

    /**
     * Gets the singleton cache object
     */
    public static MoleculeCache getMoleculeCache() {
        return moleculeCache;
    }

    public interface UseCacheMolecule {
        void useMolecule(ROMol mol) ;
    }

    /**
     * Creates a molecule for a smiles and adds it to the cache.
     *
     * @param smiles
     * @return
     */
    private ROMol getMolecule(String smiles, boolean trusted) {
        logger.debug("Request for smiles " + smiles);

        if (molecules.containsKey(smiles)) {
            var mol = molecules.get(smiles);
            if (mol != null) {
                nHits.incrementAndGet();
                logger.debug("Hit cache");
                return mol;
            } else {
                // the molecule in the cache is null. This means that another
                // thread is in the process of removing the molecule from the
                // LRU cache
                logger.debug("Null molecule in LRU cache!");
            }
        }
        logger.debug("Parsing smiles");

        var mol = RDKitOps.smilesToMol(smiles, trusted).orElse(null);

        molecules.put(smiles, mol);
        logger.debug("put compound into cache");
        nPuts.incrementAndGet();

        return mol;
    }

    public void useMolecule(UseCacheMolecule useCacheMolecule, String smiles) {
        useMolecule(useCacheMolecule, smiles, false);
    }

    /**
     * Gets a molecule from the cache if present, otherwise generates and puts
     * into the cache (if addToCache is set).
     *
     * @param useCacheMolecule
     * @param smiles
     */
    public void useMolecule(UseCacheMolecule useCacheMolecule, String smiles, boolean trusted) {

        // make sure no other threads can check out this structure
        ReentrantLock lock = null;
        if (!ALLOW_CONCURRENT_TARGET_ACCESS) {
            synchronized (structureLockMap) {
                if (!structureLockMap.containsKey(smiles))
                    structureLockMap.put(smiles, new ReentrantLock());
                lock = structureLockMap.get(smiles);
            }
            if (logger.isDebugEnabled())
                logger.debug("Attempting to acquire lock for smiles " + smiles
                        + " current hold count " + lock.getHoldCount());
            // don't acquire the lock in the synchronized block- deadlock
            lock.lock();
        } else {
            synchronized (moleculeUseCount) {
                if (!moleculeUseCount.containsKey(smiles))
                    moleculeUseCount.put(smiles, 1);
                else {
                    moleculeUseCount.put(smiles, moleculeUseCount.get(smiles) + 1);
                    logger.warn("Multiple use!");
                }
            }
        }
        try {
            if (logger.isDebugEnabled())
                logger.debug("Acquired lock for smiles " + smiles + " "
                        + lock.getHoldCount() + " holds left");

            var mol = getMolecule(smiles, trusted);
            if (mol != null) {
                useCacheMolecule.useMolecule(mol);
            }

            if (!ALLOW_CONCURRENT_TARGET_ACCESS) {
                returnMol(mol, smiles);
            } else {
                synchronized (moleculeUseCount) {
                    int cnt = moleculeUseCount.get(smiles);
                    if (cnt == 1) {
                        moleculeUseCount.remove(smiles);
                        returnMol(mol, smiles);
                    } else {
                        moleculeUseCount.put(smiles, cnt - 1);
                    }
                }
            }

        } finally {
            // free lock and delete lock object if nobody else is holding
            if (!ALLOW_CONCURRENT_TARGET_ACCESS) {
                synchronized (structureLockMap) {
                    lock.unlock();
                    if (logger.isDebugEnabled())
                        logger.debug("Removed lock for smiles " + smiles + " "
                                + lock.getHoldCount() + " holds left");
                    // make sure the map removal is done in the synchronized
                    // block
                    if (!lock.hasQueuedThreads()) {
                        structureLockMap.remove(smiles);
                    }
                }
            }
        }

    }

    /**
     * Returns a molecule to the cache after use.
     *
     * @param mol
     * @param smiles
     */
    private void returnMol(ROMol mol, String smiles) {
    }

    /**
     * Logs information about the cache
     */
    public void info() {
        logger.info("Cache Usage hits " + nHits.get() + " puts " + nPuts.get() + " removals "
                + nRemovals.get());
        // printMemoryUsage();
    }

    /**
     * Subclass LRUmap so that we can reuse the molecule and avoid object churn.
     */
    class MoleculeLRUMap extends LRUMap {

        private static final long serialVersionUID = 1000L;

        MoleculeLRUMap(int size) {
            super(size);
        }

        /*
         * Clean up the molecule by freeing/deleting it.
         *
         * (non-Javadoc)
         *
         * @see
         * org.apache.commons.collections.map.LRUMap#removeLRU(org.apache.commons
         * .collections.map.AbstractLinkedMap.LinkEntry)
         */
        @Override
        protected boolean removeLRU(LinkEntry entry) {
            logger.debug("removing molecule");

            // make sure two threads don't end up deleting the same entry
            final var mol = (ROMol) entry.getValue();
            final String smiles = (String) entry.getKey();

            boolean molInUse;
            if (!ALLOW_CONCURRENT_TARGET_ACCESS) {
                synchronized (structureLockMap) {
                    // determine if this entry is still being used by another
                    // thread.
                    molInUse = structureLockMap.containsKey(smiles);
                    // set the entry to null here in case another thread picks
                    // it up
                    // before the entry is properly removed
                    entry.setValue(null);
                }
            } else {
                synchronized (moleculeUseCount) {
                    molInUse = moleculeUseCount.containsKey(smiles);
                    entry.setValue(null);
                }
            }
            if (molInUse) {
                // the molecule is in use in another thread- create a reaper to
                // delete it which will wait until the molecule is not in use.
                String threadName = "cacheDeleteMol" + CommonUtils.getUniqLabel();
                Thread t = new Thread(threadName) {
                    @Override
                    public void run() {
                        try {
                            int pass = 1;
                            while (true) {
                                // sleep for a while
                                Thread.sleep(100L);
                                // Check if the molecule is in use. Strictly
                                // speaking this test does not guarantee that
                                // this molecule is in use (the entry could have
                                // been deleted then recreated), but if this
                                // tests false then it is safe to remove the
                                // molecule.
                                boolean molInUse;
                                if (!ALLOW_CONCURRENT_TARGET_ACCESS) {
                                    synchronized (structureLockMap) {
                                        molInUse = structureLockMap.containsKey(smiles);
                                    }
                                } else {
                                    synchronized (moleculeUseCount) {
                                        molInUse = moleculeUseCount.containsKey(smiles);
                                    }
                                }
                                if (!molInUse) {
                                    mol.delete();
                                    logger.debug("Deleted in use cache molecule, pass "
                                            + pass);
                                    return;
                                }
                                pass++;
                            }
                        } catch (InterruptedException e) {
                            logger.error("Molecule delete thread interrupted!", e);
                            Thread.currentThread().interrupt();
                        }
                    }
                };
                t.start();
                logger.warn("Unable to delete in use cache molecule-reaper thread started");
            } else
                mol.delete();

            nRemovals.incrementAndGet();
            return true;
        }

    }

    /**
     * @return the useMoleculeCache
     */
    public static boolean isUseMoleculeCache() {
        return useMoleculeCache;
    }

    /**
     * @param useMoleculeCache the useMoleculeCache to set
     */
    public static void setUseMoleculeCache(boolean useMoleculeCache) {
        MoleculeCache.useMoleculeCache = useMoleculeCache;
    }

}
