package com.cairn.common;

import com.google.common.collect.Streams;
import org.RDKit.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.Precision;
import org.apache.log4j.Logger;

import java.util.*;


public class RDKitOps {

    public enum ExtendedFingerPrintType {
        ECFP4, ECFP6, FCFP4, FCFP6;

        int getRadius() {
            switch (this) {
                case ECFP4:
                case FCFP4:
                    return 2;
                case FCFP6:
                case ECFP6:
                    return 3;
                default:
                    throw new IllegalArgumentException();
            }
        }

        boolean useFeatures() {
            switch (this) {
                case FCFP4:
                case FCFP6:
                    return true;
                case ECFP4:
                case ECFP6:
                    return false;
                default:
                    throw new IllegalArgumentException();
            }
        }

        public SparseIntVectu32 createFingerprint(ROMol mol) {
            var radius = 3;
            var useFeatures = false;
            switch (this) {
                case FCFP4:
                    radius = 2;
                    useFeatures = true;
                    break;
                case FCFP6:
                    useFeatures = true;
                    break;
                case ECFP4:
                    radius = 2;
                    break;
                case ECFP6:
                    break;
                default:
                    throw new IllegalArgumentException();
            }
            if (useFeatures) {
                return RDKFuncs.getFeatureFingerprint(mol, radius);
            } else {
                return RDKFuncs.MorganFingerprintMol(mol, radius, null, null, false, true, true);
            }

        }
    }

    private static final Logger logger = Logger.getLogger(RDKitOps.class);
    public static final int PATTERN_FP_SIZE = 2048;

    private RDKitOps() {
    }

    public static Optional<String> canonicalize(String smilesIn) {
        return smilesToMol(smilesIn).map(RDKitOps::canonicalize);
    }

    public static String canonicalize(ROMol mol) {
        return mol.MolToSmiles(true);
    }

    public static ExplicitBitVect patternFingerPrintMol(ROMol mol) {
        return patternFingerPrintMol(mol, 2048);
    }

    public static ExplicitBitVect patternFingerPrintMol(ROMol mol, int fingerprintSize) {
        return RDKFuncs.PatternFingerprintMol(mol, fingerprintSize);
    }

    public static double similarity(ExplicitBitVect fp1, ExplicitBitVect fp2) {
        return RDKFuncs.TanimotoSimilarity(fp1, fp2);
    }

    public static Optional<ROMol> smilesToMol(String smiles) {
        return smilesToMol(smiles, false);
    }

    public static Optional<ROMol> smilesToMol(String smiles, boolean trusted) {
        if (StringUtils.isBlank(smiles)) {
            var msg = "smilesToMol: empty or null smiles";
            logger.error(msg);
            throw new IllegalArgumentException(msg);
        }
        try {
            RWMol mol = null;
            try {
                mol = RWMol.MolFromSmiles(smiles, 0, !trusted);
                if (trusted)
                    mol.updatePropertyCache();
                return mol == null ? Optional.empty() : Optional.of(mol);
            } catch (MolSanitizeException ex) {
                if (mol == null)
                    mol = RWMol.MolFromSmiles(smiles, 0, false);
                if (mol != null) {
                    mol.updatePropertyCache(false);
                    var ops = SanitizeFlags.SANITIZE_ALL.swigValue() ^ SanitizeFlags.SANITIZE_PROPERTIES.swigValue();
                    RDKFuncs.sanitizeMol(mol, ops);
                }
                logger.debug("Failed to fully sanitize smiles (bad valance) " + smiles);
                if (mol == null) {
                    logger.warn("Unable to parse smiles " + smiles);
                    return Optional.empty();
                } else
                    return Optional.of(mol);
            }
        } catch (GenericRDKitException ex) {
            logger.warn("Failed to parse smiles " + smiles);
            return Optional.empty();
        }
    }

    public static Optional<ROMol> smartsToMol(String smarts) {
        try {
            var mol = RWMol.MolFromSmarts(smarts);
            return mol == null ? Optional.empty() : Optional.of(mol);
        } catch (GenericRDKitException ex) {
            logger.warn("Failed to parse smarts " + smarts);
            return Optional.empty();
        }
    }

    public static Optional<ROMol> sdfToMol(String sdf) {
        try {
            try {
                var mol = RWMol.MolFromMolBlock(sdf, true, true);
                return mol == null ? Optional.empty() : Optional.of(mol);
            } catch (MolSanitizeException | GenericRDKitException ex) {
                var mol = RWMol.MolFromMolBlock(sdf, false, false);
                if (mol != null) {
                    mol.updatePropertyCache(false);
                    var ops = SanitizeFlags.SANITIZE_ALL.swigValue() ^ SanitizeFlags.SANITIZE_PROPERTIES.swigValue();
                    RDKFuncs.sanitizeMol(mol, ops);
                }
                return mol == null ? Optional.empty() : Optional.of(mol);
            }
        } catch (GenericRDKitException ex) {
            logger.warn("Failed to parse sdf entry " + sdf);
            return Optional.empty();
        }
    }

    public static String molInfo(ROMol mol) {
        var sb = new StringBuilder();
        var atoms = mol.getAtoms();
        for (var i = 0; i < atoms.size(); i++) {
            sb.append(atomInfo(atoms.get(i))).append("\n");
        }
        return sb.toString();
    }

    public static String atomInfo(Atom atom) {
        var index = atom.getIdx();
        var atomicNo = atom.getAtomicNum();
        var symbol = atom.getSymbol();
        var chirality = atom.getChiralTag().name();

        var sb = new StringBuilder();
        sb.append(index).append(' ').append(symbol).append(' ').append(atomicNo).append(' ')
                .append(chirality);

        return sb.toString();
    }

    public static List<Integer> molToBinary(ROMol mol) {
        var cPickle = mol.ToBinary();
        var size = cPickle.size();
        assert size < Integer.MAX_VALUE;
        var isize = (int) size;
        var pickle = new ArrayList<Integer>(isize);
        for (var i = 0; i < isize; i++)
            pickle.add(i);
        return pickle;
    }

    public static ROMol molFromBinary(List<Integer> binary) {
        var cPickle = new Int_Vect(binary.size());
        Streams.mapWithIndex(binary.stream(), (v, i) -> {
            cPickle.set((int) i, v);
            return v;
        });
        var mol = ROMol.MolFromBinary(cPickle);
        return mol;
    }

    public static Optional<List<String>> getComponents(String smilesIn) {
        return smilesToMol(smilesIn).map (mol -> {
            ROMol_Vect components;
            try {
                components = RDKFuncs.getMolFrags(mol, true);
            } catch (MolSanitizeException ex) {
                components = RDKFuncs.getMolFrags(mol, false);
            }
            var size = (int) components.size();
            var smilesOut = new ArrayList<String>(size);
            for (var i = 0; i < size; i++) {
                var smi = canonicalize(components.get(i));
                smilesOut.add(smi);
            }
            return smilesOut;
        });
    }

    public static Map<Long, Integer> fingerprintToMap(SparseIntVectu32 fp) {
        assert fp.getLength() == ((long) Integer.MAX_VALUE) * 2L + 1L;
        var map = new HashMap<Long, Integer>();
        var elements = fp.getNonzero();
        for (var i = 0; i < elements.size(); i++) {
            var el = elements.get(i);
            map.put(el.getFirst(), el.getSecond());
        }
        return map;
    }

    public static SparseIntVectu32 mapToFingerprint(Map<Long, Integer> map) {
        return mapToFingerprint(map, ((long) Integer.MAX_VALUE) * 2L + 1L);
    }

    public static SparseIntVectu32 mapToFingerprint(Map<Long, Integer> map, long size) {
        var fp = new SparseIntVectu32(size);
        map.forEach(fp::setVal);
        return fp;
    }

    /**
     * @param structure1
     * @param structure2
     * @return similarity between two smiles structures using Pattern fingerprints
     */
    public static double calculateSimilarity(String structure1, String structure2) {
        var fingerprint1 = RDKitOps.smilesToMol(structure1).map(RDKitOps::patternFingerPrintMol);
        var fingerprint2 = RDKitOps.smilesToMol(structure2).map(RDKitOps::patternFingerPrintMol);
        if (fingerprint1.isEmpty() || fingerprint2.isEmpty())
            return -1.0;
        return RDKitOps.similarity(fingerprint1.get(), fingerprint2.get());
    }


    public static double molecularWeight(ROMol mol) {
        var hydrogenWt = PeriodicTable.getTable().getMostCommonIsotopeMass("H");
        var atoms = mol.getAtoms();
        var molecularWeight = .0;
        for (var atomNo = 0; atomNo < atoms.size(); atomNo++) {
            var atom = mol.getAtomWithIdx(atomNo);
            molecularWeight += atom.getMass();
            molecularWeight += atom.getTotalNumHs() * hydrogenWt;
        }
        return molecularWeight;
    }


    public enum MolFormat {
        SMARTS, SMILES, CAN_SMILES, MDL, MOL, MOL2, PDB, MOLECULAR_FORMULA;

        public static MolFormat fromString(String str) {
            str = str.toUpperCase().replace(' ', '_');
            return MolFormat.valueOf(str);
        }
    }

    public static Optional<ROMol> stringToMolecule(String str, MolFormat format) {
        try {
            switch (format) {
                case SMARTS:
                    return RDKitOps.smartsToMol(str);
                case SMILES:
                case CAN_SMILES:
                    return RDKitOps.smilesToMol(str);
                case MDL:
                case MOL:
                    str = str.replace('|', '\n');
                    return RDKitOps.sdfToMol(str);
                case MOL2:
                    str = str.replace('|', '\n');
                    var mol2 = RDKFuncs.Mol2BlockToMol(str);
                    return mol2 == null ? Optional.empty() : Optional.of(mol2);
                case PDB:
                    var pdb = RDKFuncs.PDBBlockToMol(str);
                    return pdb == null ? Optional.empty() : Optional.of(pdb);
                case MOLECULAR_FORMULA:
                    throw new IllegalArgumentException("Unable to convert formula to molecule");
                default:
                    throw new IllegalArgumentException("Unknown format" + format);
            }
        } catch (MolSanitizeException | GenericRDKitException ex) {
            return Optional.empty();
        }
    }


    public static String moleculeToString(ROMol mol, MolFormat format) {
        switch (format) {
            case SMARTS:
                return RDKFuncs.MolToSmarts(mol);
            case SMILES:
            case CAN_SMILES:
                return mol.MolToSmiles(true);
            case MDL:
            case MOL:
                try {
                    return mol.MolToMolBlock();
                } catch (MolSanitizeException ex) {
                    return RDKFuncs.MolToMolBlock(mol, true, -1, false);
                }
            case MOL2:
                throw new IllegalArgumentException("Unable to convert to MOL2 block");
            case PDB:
                return mol.MolToPDBBlock();
            case MOLECULAR_FORMULA:
                return RDKFuncs.calcMolFormula(mol);
            default:
                throw new IllegalArgumentException("Unknown format" + format);
        }
    }

    public static String moleculeToSvg(ROMol mol, int width, int height) {
        var drawMol = new RWMol(mol);
        if (drawMol.getNumConformers() > 0) {
            var conformer = drawMol.getConformer();
            if (!conformer.is3D()) {
                var nBonds = drawMol.getNumBonds();
                var nCC = 0;
                var sum = .0;
                for (var b = 0; b<nBonds; b++) {
                    var bond = drawMol.getBondWithIdx(b);
                    if (bond.getBeginAtom().getAtomicNum() == 6 &&
                    bond.getEndAtom().getAtomicNum() == 6) {
                        var point1 = conformer.getAtomPos(bond.getBeginAtomIdx());
                                var point2 = conformer.getAtomPos(bond.getEndAtomIdx());
                                var x = point1.getX() - point2.getX();
                                var y = point1.getY() - point2.getY();
                                var distance = FastMath.sqrt(x*x+y*y);
                                nCC++;
                                sum += distance;
                    }
                }
                var avgCC = sum/((double) nCC);
                if (!Precision.equals(avgCC, 1.5, 0.01)) {
                    var scale = 1.5/avgCC;
                    var numAtoms = drawMol.getNumAtoms();
                    for (var a = 0; a<numAtoms; a++) {
                        var point = conformer.getAtomPos(a);
                        point.setX(point.getX()*scale);
                        point.setY(point.getY()*scale);
                    }
                }
            }
        }

        // 2nd argument to perpareMolForDrawing is kekulize flag
        try {
            RDKFuncs.prepareMolForDrawing(drawMol, true);
        } catch (MolSanitizeException ex) {
            RDKFuncs.prepareMolForDrawing(drawMol, false);
        }
        var drawer = new MolDraw2DSVG(width, height);
        drawer.setLineWidth(1);
        var scale = width <= 150 ? 2.5 : 1.75;
        drawer.setFontSize(drawer.fontSize()*scale);
        drawer.drawMolecule(drawMol);
        drawer.finishDrawing();
        var svg = drawer.getDrawingText();
        drawer.delete();
        drawMol.delete();
        return svg;
    }

    public static BitSet explictBitVectToBitSet(ExplicitBitVect vect) {
        var bitSet = new BitSet((int)vect.size());
        var onBits = vect.getOnBits();
        for (var i=0; i<onBits.size(); i++) {
            bitSet.set(onBits.get(i));
        }
        return bitSet;
    }
}
