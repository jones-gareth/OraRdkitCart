package com.cairn.rmi.test.client;

import com.cairn.common.RDKitOps;
import com.cairn.common.SqlFetcher;
import com.cairn.rmi.util.LoadSmiles;
import org.apache.log4j.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static com.cairn.rmi.test.client.ClientUtil.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.MatcherAssert.assertThat;

public class TestClobColumn {
    private static final List<LoadSmiles.SmilesAndId> en1000 = ClientUtil.loadFromSmilesIntoMemory("/en1000.smi.gz");
    private static final Logger logger = Logger.getLogger(TestClobColumn.class);

    @BeforeAll
    static void createClobTable() throws SQLException, IOException {
        setup();
        var tableName = "en1000_sdf";
        try (var connection = getTestConnection()) {
            var query = "select table_name from user_tables where table_name = ?";
            var test = SqlFetcher.objectToString(SqlFetcher.fetchSingleValue(
                    connection, query, new Object[]{tableName.toUpperCase()}));

            if (test != null) {
                logger.info("Dropping table " + tableName);
                var update = "drop table " + tableName;
                SqlFetcher.updateCommand(connection, update, null);
            }
            logger.info("creating table " + tableName);
            var update = "create table " + tableName
                    + "(id varchar2(100) primary key, sdf clob)";
            SqlFetcher.updateCommand(connection, update, null);

            var insert = "insert into " + tableName + " (id, sdf) " + "values (:1, :2)";
            try (var is = TestClobColumn.class.getResourceAsStream("/en1000.smi.gz");
                 var zipStream = new GZIPInputStream(is);
                 var in = new BufferedReader(new InputStreamReader(zipStream));
                 var statement = connection.prepareStatement(insert)) {
                int count = 0;
                while (true) {
                    String line = in.readLine();
                    if (line == null)
                        break;

                    int pos = line.indexOf(' ');
                    String smiles = line.substring(0, pos);
                    var sdOpt = RDKitOps.smilesToMol(smiles).map(mol -> RDKitOps.moleculeToString(mol, RDKitOps.MolFormat.MDL));
                    if (sdOpt.isEmpty())
                        continue;

                    String idStr = line.substring(pos + 1);
                    String sd = sdOpt.get();
                    var clob = connection.createClob();
                    clob.setString(1, sd);

                    statement.setString(1, idStr);
                    statement.setClob(2, clob);
                    statement.execute();

                    clob.free();
                    count++;
                    if (count % 1000 == 0)
                        logger.info("loaded " + count + " compounds");
                }
                connection.commit();
                logger.info("Finished: loaded " + count + " compounds");
            }

            update = "create index en1000_sdf_structure_index on " + tableName + "(sdf) indextype is c$cschem1.structureIndexType";
            SqlFetcher.updateCommand(connection, update, null);
        }
    }

    @AfterAll
    static void deleteTable() {
        executeWithConnection(connection -> {
            var update = "drop table en1000_sdf";
            SqlFetcher.updateCommand(connection, update, null);
        });
    }

    @ParameterizedTest
    @MethodSource("queries")
    void substructureSearchOnSdfColumn(String query) throws Exception {
        try (var connection = getTestConnection()) {
            var sqlQuery = "select id, sdf from en1000_sdf where c$cschem1.substructure(sdf, ?, -1) = 1";
            var oraHits = commonSearch(connection, query, sqlQuery)
                    .stream().map(LoadSmiles.SmilesAndId::getId).collect(Collectors.toList());
            var memHits = substructureSearchInMemory(query, en1000)
                    .stream().map(LoadSmiles.SmilesAndId::getId).collect(Collectors.toList());
            assertEquals(oraHits.size(), memHits.size(), "Substructure search hits count error for " + query);
            assertThat(oraHits, containsInAnyOrder(memHits.toArray()));
        }
    }

    static Stream<String> queries() throws Exception {
        return TestTableIndexSubstructureSearch.queries();
    }
}
