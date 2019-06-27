package com.cairn.rmi.test.client;

import com.cairn.rmi.util.LoadSmiles;
import com.cairn.common.RDKitOps;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static com.cairn.rmi.test.client.ClientUtil.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 *
 * @author Gareth Jones
 */
class TestTableIndexExactMatchSearch {

    private static final List<LoadSmiles.SmilesAndId> en1000 = ClientUtil.loadFromSmilesIntoMemory("/en1000.smi.gz");

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
    @MethodSource("smilesQueries")
    void testSearch(LoadSmiles.SmilesAndId smilesAndId) {
        executeWithConnection(connection -> {
            var sqlQuery = "select id, smiles from en1000 where c$cschem1.exactMatch(smiles, ?, -1) = 1";

            var oraHits = commonSearch(connection, smilesAndId.getSmiles(), sqlQuery);
            assertEquals(oraHits.size(), 1);
            assertEquals(oraHits.get(0).getId(), smilesAndId.getId());

            var canSimiles = RDKitOps.canonicalize(smilesAndId.getSmiles()).get();
            assertNotEquals(canSimiles, smilesAndId.getSmiles());
            oraHits = commonSearch(connection, canSimiles, sqlQuery);
            assertEquals(oraHits.size(), 1);
            assertEquals(oraHits.get(0).getId(), smilesAndId.getId());
        });
    }

    private static Stream<Arguments> smilesQueries() {
        return en1000.stream().limit(100).map(Arguments::of);
    }

}
