package com.cairn.rmi.test.client;

import com.cairn.rmi.util.LoadSmiles;
import com.cairn.common.RDKitOps;
import org.RDKit.GenericRDKitException;
import org.RDKit.RWMol;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.cairn.rmi.test.client.ClientUtil.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.*;

import com.cairn.common.CommonUtils;

/**
 * @author Gareth Jones
 */
class TestTableIndexSubstructureSearch {

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
    @MethodSource("queries")
    void testSearchCacheOn(String query) throws Exception {
        ClientUtil.executeWithConnection(ClientUtil::cacheOn);
        testSearch(query);
    }

    @ParameterizedTest
    @MethodSource("queries")
    void testSearchCacheOff(String query) throws Exception {
        ClientUtil.executeWithConnection(ClientUtil::cacheOff);
        testSearch(query);
    }

    private void testSearch(String query) throws Exception {
        try (var connection = getTestConnection()) {
            var oraHits = substructureSearch(connection, query, "en1000");
            var memHits = substructureSearchInMemory(query, en1000);
            assertEquals(oraHits.size(), memHits.size(), "Substructure search hits count error for " + query);
            assertThat(oraHits, containsInAnyOrder(memHits.toArray()));
        }
    }

    @ParameterizedTest
    @MethodSource("groupedQueries")
    void testParallelSearchCacheOn(List<String> queries) throws Exception {
        ClientUtil.executeWithConnection(ClientUtil::cacheOn);
        testParallelSearch(queries);
    }

    @ParameterizedTest
    @MethodSource("groupedQueries")
    void testParallelSearchCacheOff(List<String> queries) throws Exception {
        ClientUtil.executeWithConnection(ClientUtil::cacheOff);
        testParallelSearch(queries);
    }


    private void testParallelSearch(List<String> queries) throws Exception {

        var startGate = new CountDownLatch(1);
        var endGate = new CountDownLatch(queries.size());
        var memHitsMap = new HashMap<String, List<LoadSmiles.SmilesAndId>>();
        queries.forEach(q -> memHitsMap.put(q, substructureSearchInMemory(q, en1000)));
        var oraHitsMap = new ConcurrentHashMap<String, List<LoadSmiles.SmilesAndId>>();

        queries.forEach(query -> {
            var thread = new Thread(() -> {
                try {
                    startGate.await();
                    ClientUtil.executeWithConnection(connection -> {
                        var oraHits = substructureSearch(connection, query, "en1000");
                        oraHitsMap.put(query, oraHits);
                    });
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                } finally {
                    endGate.countDown();
                }
            });
            thread.start();
        });

        startGate.countDown();
        endGate.await();

        queries.forEach(query -> {
            assertTrue(oraHitsMap.containsKey(query));
            assertTrue(memHitsMap.containsKey(query));
            var oraHits = oraHitsMap.get(query);
            var memHits = memHitsMap.get(query);
            assertEquals(oraHits.size(), memHits.size(), "Substructure search hits count error for " + query);
            assertThat(oraHits, containsInAnyOrder(memHits.toArray()));
        });
    }

    static Stream<String> queries() throws Exception {
        return smartsQueries().stream();
    }

    private static Stream<Arguments> groupedQueries() throws Exception {
        return ListUtils.partition(smartsQueries(), 10).stream().map(Arguments::of);
    }

    private static List<String> smartsQueries() throws Exception {
        try (var is = TestTableIndexSubstructureSearch.class.getResourceAsStream("/patty_rules.txt");
             var reader = new BufferedReader(new InputStreamReader(is))) {
            return reader.lines()
                    .filter(l -> StringUtils.isNotBlank(l) && !l.startsWith("#") && !l.startsWith(" "))
                    .map(l -> l.substring(0, l.indexOf(" ")))
                    .collect(Collectors.toList());
        }
    }

    @ParameterizedTest
    @MethodSource("mdlQueries")
    void testMdlQuerySearch(String mdl) {
        executeWithConnection(connection -> {
            var sqlQuery = "select id, smiles from en1000 where c$cschem1.mdl_substructure(smiles, ?, -1) = 1";
            var oraHits = commonSearch(connection, mdl, sqlQuery);
            var query = RDKitOps.sdfToMol(mdl).get();
            var memHits = substructureSearchInMemory(query, en1000);
            assertEquals(oraHits.size(), memHits.size(), "Substructure search hits count error for " + query);
            assertThat(oraHits, containsInAnyOrder(memHits.toArray()));
        });
    }

    private static Stream<String> mdlQueries() {
        // TODO check why mdlQuery1 throws GenericRDKitException
        return Stream.of("/mdlQuery2.sdf") //, "/mdlQuery1.sdf")
                .map(f -> CommonUtils.resourceToString(TestTableIndexSubstructureSearch.class, f));
    }


    @ParameterizedTest
    @MethodSource("queries")
    void testSubstructureQueryFilter(String query) {
        executeWithConnection(connection -> {
            var sqlQuery = "select id, smiles from en1000 m where rowid in " +
                    "(select * from table( c$cschem1.chem_structure.tableIndexSubstructSqlFilter('CSCHEM1_TEST', " +
                    "'EN1000', 'SMILES', 'select rowid from cschem1_test.en1000 where id like ''ZT%''', ?, -1)))";
            var oraHits = commonSearch(connection, query, sqlQuery);
            var memHits = substructureSearchInMemory(query, en1000).stream()
                    .filter(h -> h.getId()
                    .startsWith("ZT")).collect(Collectors.toList());
            assertEquals(oraHits.size(), memHits.size(), "Substructure search hits count error for " + query);
            assertThat(oraHits, containsInAnyOrder(memHits.toArray()));
        });
    }

    @Test
    void testQueryFailsOnTrustedSmiles() {
        var query = "[#7;D3](*=*)(-&!@*)*:*";
        var target = "COc1ccc2cc(C3(C)NC(=O)N(CC(=O)C(C)(C)C)C3=O)ccc2c1";

        var queryMol = RWMol.MolFromSmarts(query);
        var mol = RWMol.MolFromSmiles(target);
        var canSmi = mol.MolToSmiles(true);

        var mol2 = RWMol.MolFromSmiles(canSmi, 0, false);
        mol2.updatePropertyCache();
        assertThrows(GenericRDKitException.class, () -> mol2.hasSubstructMatch(queryMol));
        mol.hasSubstructMatch(queryMol);
    }

}
