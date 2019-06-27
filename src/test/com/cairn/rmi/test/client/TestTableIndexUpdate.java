package com.cairn.rmi.test.client;

import com.cairn.common.SqlFetcher;
import com.cairn.common.SqlUtil;
import com.cairn.rmi.util.LoadSmiles;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.util.List;
import java.util.stream.Collectors;

import static com.cairn.rmi.test.client.ClientUtil.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Gareth Jones
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestTableIndexUpdate {

    private static final String query = "c1ccccc1CC";
    private static final int nToDelete = 20;
    private static final int nToInsert = 10;

    private static List<String> idsToRemove;
    private static List<String> idsToInsert;
    private static List<LoadSmiles.SmilesAndId> memHits;
    private static List<LoadSmiles.SmilesAndId> expectedHitsAfterDelete;
    private static List<LoadSmiles.SmilesAndId> expectedHitsAfterInsert;
    private static final List<LoadSmiles.SmilesAndId> en1000 = ClientUtil.loadFromSmilesIntoMemory("/en1000.smi.gz");

    @BeforeAll
    static void init() throws Exception {
        setup();
        loadSmilesIntoTable("/en1000.smi.gz", "en1000");
        createStructureIndex("en1000", "en1000_smiles_index");
        memHits = substructureSearchInMemory(query, en1000);
        idsToRemove = memHits.stream().limit(nToDelete).map(LoadSmiles.SmilesAndId::getId).collect(Collectors.toList());
        idsToInsert = memHits.stream().limit(nToInsert).map(LoadSmiles.SmilesAndId::getId).collect(Collectors.toList());

        expectedHitsAfterDelete = memHits.stream().filter(h -> !idsToRemove.contains(h.getId())).collect(Collectors.toList());
        expectedHitsAfterInsert = memHits.stream().filter(h -> !idsToRemove.contains(h.getId()) || idsToInsert.contains(h.getId())).collect(Collectors.toList());
    }

    @AfterAll
    static void finish() {
        dropStructureIndex("en1000_smiles_index");
    }


    void checkHitsAfterDelete(Connection connection) {
        var oraHits = substructureSearch(connection, query, "en1000");
        assertEquals(oraHits.size(), expectedHitsAfterDelete.size(), "Substructure search hits error after editing");
        assertThat(oraHits, containsInAnyOrder(expectedHitsAfterDelete.toArray()));
    }

    void checkHitsAfterInsert(Connection connection) {
        var oraHits = substructureSearch(connection, query, "en1000");
        assertEquals(oraHits.size(), expectedHitsAfterInsert.size(), "Substructure search hits error after editing");
        assertThat(oraHits, containsInAnyOrder(expectedHitsAfterInsert.toArray()));
    }

    @Test
    @Order(1)
    void initialSearch() {
        executeWithConnection(connection -> {
            var oraHits = substructureSearch(connection, query, "en1000");
            assertEquals(oraHits.size(), memHits.size(), "Substructure search hits error");
            assertThat(oraHits, containsInAnyOrder(memHits.toArray()));

            var update = "delete from en1000 where id = ?";
            idsToRemove.forEach(id -> {
                var no = SqlFetcher.updateCommand(connection, update, new Object[]{id});
                assertEquals(no, 1);
            });
            SqlUtil.commitConnection(connection);
        });
    }

    void checkUnloadAfterDelete() {
        executeWithConnection(connection -> {
            var update = "alter index en1000_smiles_index parameters('unload')";
            SqlFetcher.updateCommand(connection, update, null);
            checkHitsAfterDelete(connection);
        });
    }


    void checkUnloadAfterInsert() {
        executeWithConnection(connection -> {
            var update = "alter index en1000_smiles_index parameters('unload')";
            SqlFetcher.updateCommand(connection, update, null);
            checkHitsAfterInsert(connection);
        });
    }

    @Test
    @Order(2)
    void checkEdits() {
        executeWithConnection(this::checkHitsAfterDelete);
    }

    @Test
    @Order(3)
    void checkUnload1() {
        checkUnloadAfterDelete();
    }

    @Test
    @Order(4)
    void checkRebuild() {
        executeWithConnection(connection -> {
            var update = "alter index en1000_smiles_index rebuild";
            SqlFetcher.updateCommand(connection, update, null);
            checkHitsAfterDelete(connection);
        });
    }

    @Test
    @Order(5)
    void checkUnload2() {
        checkUnloadAfterDelete();
    }

    @Test
    @Order(6)
    void checkFullRebuild() {
        executeWithConnection(connection -> {
            var update = "alter index en1000_smiles_index rebuild parameters('full')";
            SqlFetcher.updateCommand(connection, update, null);
            checkHitsAfterDelete(connection);
        });
    }


    @Test
    @Order(7)
    void checkUnload3() {
        checkUnloadAfterDelete();
    }

    @Test
    @Order(8)
    void checkAfterInsert() {
        executeWithConnection(connection -> {
            var update = "insert into en1000(id, smiles) values (?, ?)";
            idsToInsert.forEach(id -> {
                var smiles = en1000.stream().filter(e -> e.getId().equals(id)).findFirst().get();
                var no = SqlFetcher.updateCommand(connection, update, new Object[]{id, smiles.getSmiles()});
                assertEquals(no, 1);
            });
            SqlUtil.commitConnection(connection);
            checkHitsAfterInsert(connection);
        });
    }


    @Test
    @Order(9)
    void checkUnload4() {
        checkUnloadAfterInsert();
    }


    @Test
    @Order(10)
    void checkRebuild2() {
        executeWithConnection(connection -> {
            var update = "alter index en1000_smiles_index rebuild";
            SqlFetcher.updateCommand(connection, update, null);
            checkHitsAfterInsert(connection);
        });
    }

    @Test
    @Order(11)
    void checkUnload5() {
        checkUnloadAfterInsert();
    }

    @Test
    @Order(12)
    void checkFullRebuild2() {
        executeWithConnection(connection -> {
            var update = "alter index en1000_smiles_index rebuild parameters('full')";
            SqlFetcher.updateCommand(connection, update, null);
            checkHitsAfterInsert(connection);
        });
    }

    @Test
    @Order(13)
    void checkUnload6() {
        checkUnloadAfterInsert();
    }

}
