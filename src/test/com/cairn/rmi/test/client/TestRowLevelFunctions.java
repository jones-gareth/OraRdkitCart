package com.cairn.rmi.test.client;

import com.cairn.common.RDKitOps;
import com.cairn.common.SqlFetcher;
import com.cairn.common.SqlUtil;
import com.cairn.rmi.server.TaskManagerImpl;
import com.cairn.rmi.util.LoadSmiles;
import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static com.cairn.rmi.test.client.ClientUtil.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Gareth Jones
 */
class TestRowLevelFunctions {
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


    @Test
    void testExtractSmiles() {
        executeWithConnection(connection -> {
            var update = "alter table en1000 add(canonical_smiles varchar2(1000), string_fingerprint varchar2(2000))";
            SqlFetcher.updateCommand(connection, update, null);
            var password = TaskManagerImpl.getProperties().getProperty("credentials.test_password");
            update = "begin\n" +
                    "c$cschem1.chem_structure.tableIndexExtractSmiles (\n" +
                    "'cschem1_test', 'en1000', 'smiles',\n" +
                    "'select rowid, id from en1000',\n" +
                    "'update en1000 set canonical_smiles = :1, string_fingerprint = :2 where id = :3',\n" +
                    "'CSCHEM1_TEST', '" + password + "');\n" +
                    "end;";
            SqlFetcher.updateCommand(connection, update, null);
            SqlUtil.commitConnection(connection);

            var test = SqlFetcher.fetchSingleValue(connection, "select id from en1000 where canonical_smiles is null", null);
            assertNull(test);
            test = SqlFetcher.fetchSingleValue(connection, "select id from en1000 where canonical_smiles is null", null);
            assertNull(test);

            // TODO - validate that correct fingerprints and smiles are stored in the new columns
        });
    }

    @ParameterizedTest
    @MethodSource("ids")
    void testGetIdSmiles(String id) {
        executeWithConnection(connection -> {
            var query = "select smiles from en1000 where id = ?";
            var tableSmiles = SqlFetcher.objectToString(SqlFetcher.fetchSingleValue(connection, query, new Object[]{id}));
            query = "select c$cschem1.chem_structure.tableIndexGetIdSmiles('cschem1_test', 'en1000', 'smiles', 'id', ?) from dual";
            var indexSmiles = SqlFetcher.objectToString(SqlFetcher.fetchSingleValue(connection, query, new Object[]{id}));

            var canSmiles = RDKitOps.canonicalize(tableSmiles).get();
            assertNotEquals(canSmiles, tableSmiles);
            assertEquals(canSmiles, indexSmiles);
        });
    }

    @ParameterizedTest
    @MethodSource("ids")
    void testGetIdFingerprint(String id) {
        executeWithConnection(connection -> {
            var query = "select smiles from en1000 where id = ?";
            var tableSmiles = SqlFetcher.objectToString(SqlFetcher.fetchSingleValue(connection, query, new Object[]{id}));
            query = "select c$cschem1.chem_structure.tableIndexGetIdFingerprint('cschem1_test', 'en1000', 'smiles', 'id', ?) from dual";
            var indexHexFingerprint = SqlFetcher.objectToString(SqlFetcher.fetchSingleValue(connection, query, new Object[]{id}));
            var rdFingerprint = RDKitOps.smilesToMol(tableSmiles).map(RDKitOps::patternFingerPrintMol).get();
            var fingerprint = RDKitOps.explictBitVectToBitSet(rdFingerprint);
            var hexFingerprint = Base64.encodeBase64String(fingerprint.toByteArray());
            assertEquals(indexHexFingerprint, hexFingerprint);
            try {
                var indexBytes = Base64.decodeBase64(indexHexFingerprint);
                var bytes = fingerprint.toByteArray();
                assertArrayEquals(bytes, indexBytes);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    private static Stream<String> ids() {
        return en1000.stream().limit(100).map(LoadSmiles.SmilesAndId::getId);
    }

}
