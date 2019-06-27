package com.cairn.rmi.test.client;


import com.cairn.rmi.util.LoadSmiles;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.cairn.rmi.test.client.ClientUtil.*;
import static org.junit.jupiter.api.Assertions.*;


/**
 * @author Gareth Jones
 */
class TestTableIndexSimilaritySearch {
    private static final List<LoadSmiles.SmilesAndId> en1000 = ClientUtil.loadFromSmilesIntoMemory("/en1000.smi.gz");
    private static final double minSimilarity = 0.8;

    @BeforeAll
    static void init() throws Exception {
        setup();
        loadSmilesIntoTable("/en1000.smi.gz", "en1000");
        createStructureIndex("en1000", "en1000_smiles_index");
    }

    @AfterAll
    static void finish() {
        dropStructureIndex("en1000_smiles_index");
    }

    @ParameterizedTest
    @MethodSource("queries")
    void testSearch(String query) {
        executeWithConnection(connection -> {
            var oraHits = similaritySearch(connection, query, "en1000", minSimilarity);
            var memHits = similaritySearchInMemory(query, en1000, minSimilarity);
            assertFalse(oraHits.isEmpty());
            compareSimilarityHits(oraHits, memHits);
        });
    }

    static void compareSimilarityHits(List<SimilarityResult> oraHits, List<SimilarityResult> memHits) {
        assertEquals(memHits.size(), oraHits.size());
        oraHits.forEach(hit -> {
            var memHit = memHits.stream().filter(h -> h.getSmilesAndId().equals(hit.getSmilesAndId())).findFirst();
            assertTrue(memHit.isPresent());
            assertEquals(hit.getSimilarity(), memHit.get().getSimilarity(), 0.01);
        });

    }

    @ParameterizedTest
    @MethodSource("queries")
    void testSimilarityQueryFilter(String query) {
        executeWithConnection(connection -> {
            var sqlQuery = "select score, id, smiles from en1000 m, " +
                    "table( c$cschem1.chem_structure.tableIndexSimilaritySqlFilter('CSCHEM1_TEST', " +
                    "'EN1000', 'SMILES', 'select rowid from cschem1_test.en1000 where id " +
                    "like ''ZT%''', ?, " + minSimilarity + ", -1)) s where m.rowid = s.hit_rowid";
            var oraHits = commonSimilaritySearch(connection, query, sqlQuery);
            var memHits = similaritySearchInMemory(query, en1000, minSimilarity).stream()
                    .filter(h -> h.getSmilesAndId().getId().startsWith("ZT"))
                    .collect(Collectors.toList());
            compareSimilarityHits(oraHits, memHits);
        });
    }


    private static Stream<String> queries() {
        return en1000.stream().limit(100).map(LoadSmiles.SmilesAndId::getSmiles);
    }


}
