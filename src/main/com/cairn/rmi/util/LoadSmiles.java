package com.cairn.rmi.util;

import com.cairn.common.SqlFetcher;
import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

/**
 * Similar to LoadNCI, but any single file is added to a given database table in
 * the cschem1_test schema
 *
 * @author Gareth Jones
 */
public class LoadSmiles {
    private final String fileName;
    private final boolean resource;

    private static final Logger logger = Logger.getLogger(LoadSmiles.class);

    public LoadSmiles(String fileName, boolean resource) {
        this.fileName = fileName;
        this.resource = resource;
    }

    /**
     * Loads the compounds.
     *
     * @throws Exception
     */
    public void load(Connection connection, String tableName) {
        tableName = tableName.toUpperCase();

        var query = "select table_name from user_tables where table_name = ?";
        var test = SqlFetcher.objectToString(SqlFetcher.fetchSingleValue(
                connection, query, new Object[]{tableName}));

        if (test != null) {
            logger.info("Dropping table "+tableName);
            var update = "drop table "+tableName;
            SqlFetcher.updateCommand(connection, update, null);
        }
        logger.info("creating table " + tableName);
        var update = "create table " + tableName
                + "(id varchar2(100) primary key, smiles varchar2(1000))";
        SqlFetcher.updateCommand(connection, update, null);

        var insert = "insert into " + tableName + " (id, smiles) " + "values (:1, :2)";
        try (var is = resource ? this.getClass().getResourceAsStream(fileName) : new FileInputStream(fileName);
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
                String idStr = line.substring(pos + 1);

                statement.setString(1, idStr);
                statement.setString(2, smiles);
                statement.execute();

                count++;
                if (count % 1000 == 0)
                    logger.info("loaded " + count + " compounds");
            }

            connection.commit();
            logger.info("Finished: loaded " + count + " compounds");
        } catch (IOException | SQLException e) {
            String message = "Exception loading smiles";
            logger.error(message, e);
            throw new RuntimeException(message, e);
        }
    }

    public static class SmilesAndId {
        final String id;
        final String smiles;

        public SmilesAndId(String id, String smiles) {
            this.id = id;
            this.smiles = smiles;
        }

        public String getId() {
            return id;
        }

        public String getSmiles() {
            return smiles;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SmilesAndId that = (SmilesAndId) o;
            return Objects.equals(id, that.id) &&
                    Objects.equals(smiles, that.smiles);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, smiles);
        }
    }

    public List<SmilesAndId> read() {
        try (var is = resource ? this.getClass().getResourceAsStream(fileName) : new FileInputStream(fileName);
             var zipStream = new GZIPInputStream(is);
             var in = new BufferedReader(new InputStreamReader(zipStream))) {
           return  in.lines().map(line -> {
                int pos = line.indexOf(' ');
                String smiles = line.substring(0, pos);
                String idStr = line.substring(pos + 1);
                return new SmilesAndId(idStr, smiles);
            }).collect(Collectors.toList());
        } catch (IOException e) {
            String message = "Exception loading smiles";
            logger.error(message, e);
            throw new RuntimeException(message, e);
        }
    }
}
