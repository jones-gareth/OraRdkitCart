package com.cairn.rmi.test.client;

import com.cairn.common.SqlFetcher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.sql.SQLException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 * @author Gareth Jones
 */
class TestMolecularWeightTask {

    @BeforeAll
    static void init() {
        ClientUtil.setup();
    }

    @ParameterizedTest
    @MethodSource("smilesAndMw")
    void testMolecularWeight(String smiles, double expectedMw) throws SQLException {
        var query = "select c$cschem1.chem_structure.molecularWeight(?) from dual";
        try (var connection = ClientUtil.getTestConnection()) {
            var cartMw = SqlFetcher.objectToDouble(SqlFetcher.fetchSingleValue(connection, query, new Object[]{smiles}));
            assertEquals(cartMw, expectedMw, 0.01, () -> "Molecular weight mismatch for smiles " + smiles);
        }
    }


    @ParameterizedTest
    @MethodSource("smilesAndExactMass")
    void testExactMass(String smiles, double expectedMass) throws SQLException {
        var query = "select c$cschem1.chem_structure.exactMass(?) from dual";
        try (var connection = ClientUtil.getTestConnection()) {
            var cartMw = SqlFetcher.objectToDouble(SqlFetcher.fetchSingleValue(connection, query, new Object[]{smiles}));
            assertEquals(cartMw, expectedMass, 0.01, () -> "Exact mass mismatch for smiles " + smiles);
        }
    }

    // reference values from MarvinSketch

    private static Stream<Arguments> smilesAndMw() {
        return Stream.of(
                Arguments.of("c1ccccc1", 78.114),
                Arguments.of("c1ccc(cc1)C[C@H](C(=NO)O)NC(=O)N[C@]23C[C@@H]4C[C@H](C2)C[C@@H](C4)C3", 357.454),
                Arguments.of("CN1CCN(CC1)Cc2ccc3c(c2)c4c5n3CCN(C5CCC4)C(=O)C6CCCCC6", 434.628),
                Arguments.of("Cc2nc1ccccc1o2", 133.150)
        );
    }

    private static Stream<Arguments> smilesAndExactMass() {
        return Stream.of(
                Arguments.of("c1ccccc1", 78.0469501932),
                Arguments.of("c1ccc(cc1)C[C@H](C(=NO)O)NC(=O)N[C@]23C[C@@H]4C[C@H](C2)C[C@@H](C4)C3", 357.205241741),
                Arguments.of("CN1CCN(CC1)Cc2ccc3c(c2)c4c5n3CCN(C5CCC4)C(=O)C6CCCCC6", 434.304561860),
                Arguments.of("Cc2nc1ccccc1o2", 133.052763849)
        );
    }

}
