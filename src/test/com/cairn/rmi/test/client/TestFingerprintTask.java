package com.cairn.rmi.test.client;

import com.cairn.common.SqlFetcher;
import com.cairn.common.RDKitOps;
import org.RDKit.ROMol;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.sql.SQLException;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Gareth Jones
 */
class TestFingerprintTask {

    @BeforeAll
    static void init() {
        ClientUtil.setup();
    }

    @ParameterizedTest
    @MethodSource("smiles")
    void testFingerprint(String structure) throws SQLException {

        var query = "select c$cschem1.chem_structure.sqlGetFingerprint(?, ?) from dual";
        Function<ROMol, String> fpCalculator = mol -> {
            var fp = RDKitOps.patternFingerPrintMol(mol);
            return Hex.encodeHexString(fp.toByteArray());
        };

        try (var connection = ClientUtil.getTestConnection()) {
            var smilesFp = SqlFetcher.objectToString(SqlFetcher.fetchSingleValue(connection, query, new Object[]{"N", structure}));
            var testFp = RDKitOps.smilesToMol(structure).map(fpCalculator);

            assertFalse(testFp.isEmpty());
            assertEquals(smilesFp, testFp.get(), "Fingerprint match error");

            var smartsFp = SqlFetcher.objectToString(SqlFetcher.fetchSingleValue(connection, query, new Object[]{"Y", structure}));
            var testSmartsFp = RDKitOps.smartsToMol(structure).map(fpCalculator);
            assertEquals(smartsFp, testSmartsFp.get(), "Fingerprint match error");

            assertNotEquals(smilesFp, smartsFp);
        }
    }

    @ParameterizedTest
    @MethodSource("smilesPairs")
    void testTanimotoSimilarity(String structure1, String structure2) throws SQLException {
        var query = "select c$cschem1.chem_structure.tanimotoSimilarity(?, ?) from dual";

        try (var connection = ClientUtil.getTestConnection()) {
            var similarity = SqlFetcher.objectToDouble(SqlFetcher.fetchSingleValue(connection, query, new Object[]{structure1, structure2}));
            var localSimilarity = RDKitOps.calculateSimilarity(structure1, structure2);
            assertEquals(similarity, localSimilarity, 0.0001, "Similarity score mismatch");
            if (structure1.equals(structure2)) {
                assertEquals(similarity, 1.0, 0.0001, "Identical structures do not have similarity of 1");
            }
        }
    }

    private static Stream<String> smiles() {
        return Stream.of(
                "c1ccccc1",
                "c1ccc(cc1)C[C@H](C(=NO)O)NC(=O)N[C@]23C[C@@H]4C[C@H](C2)C[C@@H](C4)C3",
                "CN1CCN(CC1)Cc2ccc3c(c2)c4c5n3CCN(C5CCC4)C(=O)C6CCCCC6",
                "Cc2nc1ccccc1o2"
        );
    }

    private static Stream<Arguments> smilesPairs() {
        return smiles().flatMap( smi1 -> smiles().map( smi2 -> Arguments.of(smi1, smi2)));
    }
}
