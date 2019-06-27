package com.cairn.common;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.RDKit.SparseIntVectu32;
import org.apache.commons.codec.binary.Base64;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Value
public class FingerprintedSmiles implements Serializable {
    private static final long serialVersionUID = 1000L;

    @NonNull
    private final String smiles;
    @NonNull
    private final BitSet fingerprint;
    @Nullable
    private final List<SparseIntVectu32> deMorganFingerprints;


    public String getStringFingerprint() {
        return Base64.encodeBase64String(fingerprint.toByteArray());
    }

    private Object writeReplace() {
        var serializedFingerprint = fingerprint.toByteArray();
        List<Map<Long, Integer>> serializedMorganFingerprints = null;
        if (deMorganFingerprints != null) {
            serializedMorganFingerprints = deMorganFingerprints.stream()
                    .map(RDKitOps::fingerprintToMap).collect(Collectors.toList());
        }
        return new FingerprintedSmiles.SerializedFingerprintedSmiles(smiles, fingerprint, serializedMorganFingerprints);
    }

    @RequiredArgsConstructor
    private static class SerializedFingerprintedSmiles implements Serializable {
        private static final long serialVersionUID = 1000L;
        private final String smiles;
        private final BitSet fingerprint;
        private final List<Map<Long, Integer>> serializedMorganFingerprints;

        public Object readResolve() {
            List<SparseIntVectu32> deMorganFingerprints = null;
            if (serializedMorganFingerprints != null) {
                deMorganFingerprints = serializedMorganFingerprints
                        .stream()
                        .map(RDKitOps::mapToFingerprint)
                        .collect(Collectors.toList());
            }
            return new FingerprintedSmiles(smiles , fingerprint, deMorganFingerprints);
        }

    }
}
