package com.cairn.rmi.test.client;

import com.cairn.common.SqlFetcher;
import com.cairn.rmi.util.LoadSmiles;
import com.cairn.common.RDKitOps;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static com.cairn.rmi.test.client.ClientUtil.*;

/**
 *
 * @author Gareth Jones
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestExtendedSimilaritySearch {
    private static final List<LoadSmiles.SmilesAndId> en1000 = ClientUtil.loadFromSmilesIntoMemory("/en1000.smi.gz");
    private static final double minSimilarity = 0.6;

    @BeforeAll
    static void init() throws Exception {
        setup();
        loadSmilesIntoTable("/en1000.smi.gz", "en1000");
        createStructureIndex("en1000", "en1000_smiles_index", "fp=ecfp4 fp=fcfp6");
    }

    @AfterAll
    static void finish() {
        dropStructureIndex("en1000_smiles_index");
    }

    @ParameterizedTest
    @MethodSource("queries")
    @Order(1)
    void testEcfp4Tanimoto(String query) {
        testSearch(query, RDKitOps.ExtendedFingerPrintType.ECFP4, "tanimoto", null, null);
    }

    @ParameterizedTest
    @MethodSource("queries")
    @Order(1)
    void testFcfp6Tanimoto(String query) {
        testSearch(query, RDKitOps.ExtendedFingerPrintType.FCFP6, "tanimoto", null, null);
    }

    @ParameterizedTest
    @MethodSource("queries")
    @Order(1)
    void testEcfp4Dice(String query) {
        testSearch(query, RDKitOps.ExtendedFingerPrintType.ECFP4, "dice", null, null);
    }

    @ParameterizedTest
    @MethodSource("queries")
    @Order(1)
    void testFcfp6Dice(String query) {
        testSearch(query, RDKitOps.ExtendedFingerPrintType.FCFP6, "dice", null, null);
    }

    @ParameterizedTest
    @MethodSource("queries")
    @Order(1)
    void testEcfp4Tversky(String query) {
        testSearch(query, RDKitOps.ExtendedFingerPrintType.ECFP4, "tversky", 0.8, 0.5);
    }

    @ParameterizedTest
    @MethodSource("queries")
    @Order(1)
    void testFcfp6Tversky(String query) {
        testSearch(query, RDKitOps.ExtendedFingerPrintType.FCFP6, "tversky", 0.8, 0.5);
    }

    @Test
    @Order(2)
    void unloadIndex() {
        executeWithConnection(connection -> {
            var update = "alter index en1000_smiles_index parameters('unload')";
            SqlFetcher.updateCommand(connection, update, null);
        });
    }

    @ParameterizedTest
    @MethodSource("queries")
    @Order(3)
    void testEcfp4TanimotoAfterReload(String query) {
        testSearch(query, RDKitOps.ExtendedFingerPrintType.ECFP4, "tanimoto", null, null);
    }

    @Test
    @Order(4)
    void unloadAndRebuildIndex() {
        executeWithConnection(connection -> {
            var update = "alter index en1000_smiles_index rebuild parameters('full')";
            SqlFetcher.updateCommand(connection, update, null);
            update = "alter index en1000_smiles_index parameters('unload')";
            SqlFetcher.updateCommand(connection, update, null);
        });
    }

    @ParameterizedTest
    @MethodSource("queries")
    @Order(5)
    void testEcfp4TanimotoAfterRebuild(String query) {
        testSearch(query, RDKitOps.ExtendedFingerPrintType.ECFP4, "tanimoto", null, null);
    }

    void testSearch(String query, RDKitOps.ExtendedFingerPrintType fpType, String method, Double alpha, Double beta) {
        executeWithConnection(connection -> {
            var oraHits = extendedSimilaritySearch(connection, query, "en1000", minSimilarity, fpType.name(), method, alpha, beta);
            var memHits = extendedSimilaritySearchInMemory(fpType, query, en1000, minSimilarity, method, alpha, beta);
            TestTableIndexSimilaritySearch.compareSimilarityHits(oraHits, memHits);
        });
    }

    private static Stream<String> queries() {
        return en1000.stream().limit(25).map(LoadSmiles.SmilesAndId::getSmiles);
    }

}
