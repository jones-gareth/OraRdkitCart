package com.cairn.rmi.test.client;

import com.cairn.common.CommonUtils;
import com.cairn.common.SqlFetcher;
import com.cairn.rmi.util.LoadSmiles;
import com.cairn.common.RDKitOps;
import org.RDKit.RDKFuncs;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static com.cairn.rmi.test.client.ClientUtil.executeWithConnection;
import static com.cairn.rmi.test.client.ClientUtil.setup;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Gareth Jones
 */
class TestTranslateTask {
    private static final List<LoadSmiles.SmilesAndId> en1000 = ClientUtil.loadFromSmilesIntoMemory("/en1000.smi.gz");
    private static final String sqlQuery = "select c$cschem1.chem_structure.translateStructure (?, ?, ?) from dual";

    @BeforeAll
    static void init() {
        setup();
    }

    @ParameterizedTest
    @MethodSource("smilesQueries")
    void smilesToMol(String smiles) {
        var canSmiles = RDKitOps.canonicalize(smiles).get();
        Stream.of("smiles", "can smiles").forEach(from -> Stream.of("mdl", "mol").forEach(to -> executeWithConnection(connection -> {
            var val = SqlFetcher.fetchSingleValue(connection, sqlQuery, new Object[]{smiles, from, to});
            var molBlock = SqlFetcher.objectToString(val);
            var molOut = RDKitOps.sdfToMol(molBlock).get();
            var canSmilesOut = RDKitOps.canonicalize(molOut);
            assertEquals(canSmiles, canSmilesOut);
        })));
    }


    @ParameterizedTest
    @MethodSource("smilesQueries")
    void smilesToPdb(String smiles) {
        var canSmiles = RDKitOps.canonicalize(smiles).get();
        Stream.of("smiles", "can smiles").forEach(from -> executeWithConnection(connection -> {
            var val = SqlFetcher.fetchSingleValue(connection, sqlQuery, new Object[]{smiles, from, "pdb"});
            var pdbBlock = SqlFetcher.objectToString(val);
            var molOut = RDKFuncs.PDBBlockToMol(pdbBlock);
            var canSmilesOut = RDKitOps.canonicalize(molOut);
            assertEquals(canSmiles, canSmilesOut);
        }));
    }

    @ParameterizedTest
    @MethodSource("sdfQueries")
    void sdfToSmiles(String sdfQuery) {
        var molIn = RDKitOps.sdfToMol(sdfQuery).get();
        var canSmiles = RDKitOps.canonicalize(molIn);
        Stream.of(sdfQuery, sdfQuery.replace('\n', '|')).forEach(sdf -> Stream.of("mdl", "mol").forEach(from -> Stream.of("smiles", "can smiles").forEach(to -> {
            executeWithConnection(connection -> {
                var val = SqlFetcher.fetchSingleValue(connection, sqlQuery, new Object[]{sdf, from, to});
                var smiles = SqlFetcher.objectToString(val);
                var canSmilesOut = RDKitOps.canonicalize(smiles).get();
                assertEquals(canSmiles, canSmilesOut);
            });
        })));
    }

    /*
    This test does not work in Oracle 12, but is fine in Oracle 18
    Is presumably due to the mol2 strings being too long (> 4000)
    Could provide an clob binding if this is an issue.
     */
    @Disabled
    @ParameterizedTest
    @MethodSource("mol2Queries")
    void mol2ToSmiles(String mol2Query) {
        var molIn = RDKFuncs.Mol2BlockToMol(mol2Query);
        var canSmiles = RDKitOps.canonicalize(molIn);
        Stream.of("smiles", "can smiles").forEach(to ->
            executeWithConnection(connection -> {
                var val = SqlFetcher.fetchSingleValue(connection, sqlQuery, new Object[]{mol2Query,"mol2", to});
                var smiles = SqlFetcher.objectToString(val);
                var canSmilesOut = RDKitOps.canonicalize(smiles).get();
                assertEquals(canSmiles, canSmilesOut);
            }));
    }

    @ParameterizedTest
    @MethodSource("smilesQueries")
    void smilesToFormula(String smiles) {
        executeWithConnection(connection -> {
            var val = SqlFetcher.fetchSingleValue(connection, sqlQuery, new Object[]{smiles, "smiles", "molecular formula"});
            var formula = SqlFetcher.objectToString(val);
            var molIn = RDKitOps.smilesToMol(smiles).get();
            var formulaIn = RDKFuncs.calcMolFormula(molIn);
            assertEquals(formulaIn, formula);
        });
    }


    private static Stream<String> smilesQueries() {
        return en1000.stream().limit(10).map(LoadSmiles.SmilesAndId::getSmiles);
    }

    private static Stream<String> sdfQueries() {
        var contents = CommonUtils.resourceToString(TestTranslateTask.class, "/outNCI_few.sdf");
        var entries = contents.split("\\$\\$\\$\\$\\n");
        return Arrays.stream(entries);
    }


    private static Stream<String> mol2Queries() {
        var contents = CommonUtils.resourceToString(TestTranslateTask.class, "/5ht3.mol2");
        var entries = contents.split("(?=#\\s+Name:)");
        return Arrays.stream(entries);
    }
}
