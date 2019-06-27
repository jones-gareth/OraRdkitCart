package com.cairn.rmi.test.client;

import com.cairn.common.ModelException;
import com.cairn.common.SqlFetcher;
import com.cairn.rmi.util.LoadSmiles;
import org.junit.jupiter.api.*;

import java.sql.SQLException;
import java.util.List;

import static com.cairn.rmi.test.client.ClientUtil.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Gareth Jones
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestTableIndexBasic {
    private static final List<LoadSmiles.SmilesAndId> en1000 = ClientUtil.loadFromSmilesIntoMemory("/en1000.smi.gz");
    private static final String query = "c1ccccc1";

    @BeforeAll
    static void init() throws Exception {
        setup();
        loadSmilesIntoTable("/en1000.smi.gz", "en1000");
    }

    private void testSearch() {
        try (var connection = ClientUtil.getTestConnection()) {
            var oraHits = substructureSearch(connection, query, "en1000");
            var memHits = substructureSearchInMemory(query, en1000);
            assertEquals(oraHits.size(), memHits.size(), "Substructure search hits error");
            assertThat(oraHits, containsInAnyOrder(memHits.toArray()));
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Test
    @Order(1)
    void indexCreation() {
        executeWithConnection(connection -> {
            var update = "create index en1000_smiles_index on en1000(smiles) indextype is c$cschem1.structureIndexType";
            SqlFetcher.updateCommand(connection, update, null);
            testSearch();
        });
    }

    @Test
    @Order(1)
    void indexRebuild() {
        executeWithConnection(connection -> {
            var update = "alter index en1000_smiles_index rebuild";
            SqlFetcher.updateCommand(connection, update, null);
            testSearch();
        });
    }

    @Test
    @Order(2)
    void indexFullRebuild() {
        executeWithConnection(connection -> {
            var update = "alter index en1000_smiles_index rebuild parameters('full')";
            SqlFetcher.updateCommand(connection, update, null);
            testSearch();
        });
    }

    @Test
    @Order(3)
    void indexUnload() {
        executeWithConnection(connection -> {
            var update = "alter index en1000_smiles_index parameters('unload')";
            SqlFetcher.updateCommand(connection, update, null);
            testSearch();
        });
    }

    @Test
    @Order(4)
    void indexUnloadAndLoad() {
        executeWithConnection(connection -> {
            var update = "alter index en1000_smiles_index parameters('unload')";
            SqlFetcher.updateCommand(connection, update, null);
            update = "alter index en1000_smiles_index parameters('load')";
            SqlFetcher.updateCommand(connection, update, null);
            testSearch();
        });
    }

    @Test
    @Order(5)
    void indexAddToCache() {
        executeWithConnection(connection -> {
            var update = "alter index en1000_smiles_index parameters('add_to_cache')";
            SqlFetcher.updateCommand(connection, update, null);
            testSearch();
        });
    }

    @Test
    @Order(6)
    void indexSearchWithCacheOn() {
        executeWithConnection(connection -> {
            cacheOn(connection);
            testSearch();
        });
    }


    @Test
    @Order(7)
    void indexSearchWithCacheOff() {
        executeWithConnection(connection -> {
            cacheOff(connection);
            testSearch();
        });
    }

    @Test
    @Order(8)
    void renameColumn1() {
        executeWithConnection(connection -> {
            var update = "alter table en1000 rename column smiles to smi";
            SqlFetcher.updateCommand(connection, update, null);
            var sqlQuery = "select id, smi from en1000 where c$cschem1.substructure(smi, ?, -1) = 1";
            var oraHits = commonSearch(connection, query, sqlQuery);
            var memHits = substructureSearchInMemory(query, en1000);
            assertEquals(oraHits.size(), memHits.size(), "Substructure search hits error");
            assertThat(oraHits, containsInAnyOrder(memHits.toArray()));
        });
    }

    @Test
    @Order(9)
    void renameColumn2() {
        executeWithConnection(connection -> {
            var update = "alter table en1000 rename column smi to smiles";
            SqlFetcher.updateCommand(connection, update, null);
            testSearch();
        });
    }

    @Test
    @Order(10)
    void renameTable1() {
        executeWithConnection(connection -> {
            var update = "alter table en1000 rename to en1000_2";
            SqlFetcher.updateCommand(connection, update, null);
            var sqlQuery = "select id, smiles from en1000_2 where c$cschem1.substructure(smiles, ?, -1) = 1";
            var oraHits = commonSearch(connection, query, sqlQuery);
            var memHits = substructureSearchInMemory(query, en1000);
            assertEquals(oraHits.size(), memHits.size(), "Substructure search hits error");
            assertThat(oraHits, containsInAnyOrder(memHits.toArray()));
        });
    }

    @Test
    @Order(11)
    void renameTable2() {
        executeWithConnection(connection -> {
            var update = "alter table en1000_2 rename to en1000";
            SqlFetcher.updateCommand(connection, update, null);
            testSearch();
        });
    }


    @Test
    @Order(12)
    void indexDrop() {
        executeWithConnection(connection -> {
            var update = "drop index en1000_smiles_index";
            SqlFetcher.updateCommand(connection, update, null);
            assertThrows(ModelException.class, () -> substructureSearch(connection, query, "en1000"));
        });
    }

    @Test
    @Order(13)
    void indexCreation2() {
        executeWithConnection(connection -> {
            var update = "create index en1000_smiles_index on en1000(smiles) indextype is c$cschem1.structureIndexType";
            SqlFetcher.updateCommand(connection, update, null);
            testSearch();
        });
    }

    @Test
    @Order(14)
    void truncateTable1() {
        executeWithConnection(connection -> {
            var update = "truncate table en1000";
            SqlFetcher.updateCommand(connection, update, null);
            var sqlQuery = "select id, smiles from en1000 where c$cschem1.substructure(smiles, ?, -1) = 1";
            var oraHits = commonSearch(connection, query, sqlQuery);
            assertEquals(0, oraHits.size(), "Substructure search hits error");
        });
    }

    @Test
    @Order(15)
    void truncateUnloadAndLoad() {
        executeWithConnection(connection -> {
            var update = "alter index en1000_smiles_index parameters('unload')";
            SqlFetcher.updateCommand(connection, update, null);
            var sqlQuery = "select id, smiles from en1000 where c$cschem1.substructure(smiles, ?, -1) = 1";
            var oraHits = commonSearch(connection, query, sqlQuery);
            assertEquals(0, oraHits.size(), "Substructure search hits error");
        });
    }

}
