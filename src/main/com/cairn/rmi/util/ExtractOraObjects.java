package com.cairn.rmi.util;

import com.cairn.common.Util;
import com.cairn.common.SqlFetcher;
import com.cairn.rmi.server.TaskManagerImpl;
import com.cairn.rmi.server.TaskUtil;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Paths;
import java.sql.SQLException;

/**
 * A class to extract PL/SQL code from Oracle and save in a file.
 *
 * Used so that development may be done in JDeveloper and the code extracted  to files
 *
 * @author Gareth Jones
 */
class ExtractOraObjects {

    private static void fetchSource(String name, String type, Writer out) throws IOException {
        name = name.toUpperCase();
        type = type.toUpperCase();

        var query = "select line, text from user_source where name = ? and type = ? order by line";
        var sb = new StringBuilder();
        sb.append("create or replace ");
        try (var connection= TaskUtil.getConnection(false)) {

            var fetcher = new SqlFetcher(connection, query);
            fetcher.executeQuery(new Object[]{name, type});
            for (var row: fetcher) {
                var text = SqlFetcher.objectToString(row[1]);
                sb.append(text);
            }
            fetcher.finish();
        } catch (SQLException ex) {
            throw new RuntimeException("SQL Exception: ", ex);
        }

        sb.append("\n/\n\n");
        out.write(sb.toString());
        System.out.println("Wrote code for "+name+" .. "+type);
    }

    private static void fetchSources() throws IOException {

        var pkgFile = Paths.get(Util.getProjectRoot().getAbsolutePath(), "sql", "objects", "chem_structure_pkg.sql").toFile();
        var pkgBodyFile = Paths.get(Util.getProjectRoot().getAbsolutePath(), "sql", "objects", "chem_structure_pkg_body.sql").toFile();

        try (var out = new BufferedWriter(new FileWriter(pkgFile))) {
            fetchSource("index_common", "package", out);
            fetchSource("index_base_obj", "type", out);
            fetchSource("structure_ind_obj", "type", out);
            fetchSource("index_utl", "package", out);
            fetchSource("chem_structure", "package", out);
        }

        try (var out = new BufferedWriter(new FileWriter(pkgBodyFile))) {
            fetchSource("chem_structure", "package body", out);
            fetchSource("structure_ind_obj", "type body", out);
            fetchSource("index_utl", "package body", out);
            fetchSource("index_base_obj", "type body", out);
            fetchSource("index_common", "package body", out);
        }
    }

    public static void main(String[] argv) {
        TaskManagerImpl.loadPropertiesFile(null);
        TaskManagerImpl.setupConnection();

        try {
            fetchSources();
        } catch (IOException ex) {
            throw new RuntimeException("IOException ex");
        }
    }
}
