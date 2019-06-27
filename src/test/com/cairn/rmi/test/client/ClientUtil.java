package com.cairn.rmi.test.client;

import com.cairn.common.Util;
import com.cairn.common.SqlFetcher;
import com.cairn.common.PooledConnections;
import com.cairn.rmi.server.TaskManagerImpl;
import com.cairn.rmi.util.LoadSmiles;
import com.cairn.common.RDKitOps;
import org.RDKit.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 * @author Gareth Jones
 */
class ClientUtil {

    private ClientUtil() {
    }

    public static void executeWithConnection(Consumer<Connection> function) {
        try (var connection = ClientUtil.getTestConnection()) {
            function.accept(connection);
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static Connection getTestConnection() {
        var properties = TaskManagerImpl.getProperties();
        var password = properties.getProperty("credentials.test_password");
        var database = properties.getProperty("credentials.database");
        var host = properties.getProperty("credentials.host");
        var portStr = properties.getProperty("credentials.port");
        var connStr = "jdbc:oracle:thin:@" + host + ":" + portStr + ":" + database;
        return PooledConnections.getConnection(connStr, "cschem1_test", password);
    }

    public static void setup() {
        TaskManagerImpl.loadPropertiesFile(null);
        assertTrue(TaskManagerImpl.setupConnection(), "Unable to connect to cartridge test user");
        Util.loadRdkit();
    }

    public static void createStructureIndex(String tableName, String indexName) {
        createStructureIndex(tableName, indexName, null);
    }

    public static void createStructureIndex(String tableName, String indexName, String parameters) {
        executeWithConnection(connection -> {
            var update = "create index " + indexName + " on " + tableName + "(smiles) indextype is c$cschem1.structureIndexType";
            if (parameters != null)
                update += " parameters('" + parameters + "')";
            SqlFetcher.updateCommand(connection, update, null);
        });
    }

    public static void dropStructureIndex(String indexName) {
        executeWithConnection(connection -> {
            var update = "drop index " + indexName;
            SqlFetcher.updateCommand(connection, update, null);
        });
    }

    public static void loadSmilesIntoTable(String resource, String tableName) throws SQLException {
        var loadSmiles = new LoadSmiles(resource, true);
        try (var connection = getTestConnection()) {
            loadSmiles.load(connection, tableName);
        }
    }

    public static List<LoadSmiles.SmilesAndId> loadFromSmilesIntoMemory(String resource) {
        var loadSmiles = new LoadSmiles(resource, true);
        return loadSmiles.read();
    }


    public static List<LoadSmiles.SmilesAndId> substructureSearch(Connection connection, String query, String tableName) {
        var sqlQuery = "select id, smiles from " + tableName + " where c$cschem1.substructure(smiles, ?, -1) = 1";
        return commonSearch(connection, query, sqlQuery);
    }

    public static List<LoadSmiles.SmilesAndId> commonSearch(Connection connection, String query, String sqlQuery) {
        var fetcher = new SqlFetcher(connection, sqlQuery);
        fetcher.executeQuery(new Object[]{query});
        var hits = new ArrayList<LoadSmiles.SmilesAndId>();
        for (var row : fetcher) {
            var hit = new LoadSmiles.SmilesAndId(SqlFetcher.objectToString(row[0]), SqlFetcher.objectToString(row[1]));
            hits.add(hit);
        }
        fetcher.finish();
        return hits;
    }

    public static List<LoadSmiles.SmilesAndId> substructureSearchInMemory(String query, List<LoadSmiles.SmilesAndId> structures) {
        var queryMol = RDKitOps.smartsToMol(query).get();
        return substructureSearchInMemory(queryMol, structures);
    }

    public static List<LoadSmiles.SmilesAndId> substructureSearchInMemory(ROMol queryMol, List<LoadSmiles.SmilesAndId> structures) {
        var matchParameters = new SubstructMatchParameters();
        matchParameters.setRecursionPossible(true);
        matchParameters.setUseQueryQueryMatches(false);
        matchParameters.setUseChirality(true);

        return structures.stream().filter(item -> {
            var mol = RDKitOps.smilesToMol(item.getSmiles());
            return mol.map(m -> m.hasSubstructMatch(queryMol, matchParameters)).orElse(false);
        }).collect(Collectors.toList());
    }

    public static class SimilarityResult {
        private final LoadSmiles.SmilesAndId smilesAndId;
        private final double similarity;

        SimilarityResult(LoadSmiles.SmilesAndId smilesAndId, double similarity) {
            this.smilesAndId = smilesAndId;
            this.similarity = similarity;
        }

        public LoadSmiles.SmilesAndId getSmilesAndId() {
            return smilesAndId;
        }

        public double getSimilarity() {
            return similarity;
        }
    }

    public static List<SimilarityResult> similaritySearch(Connection connection, String query, String tableName, double minSmilarity) {
        var sqlQuery = "select c$cschem1.similarityScore(1), id, smiles from " + tableName + " where c$cschem1.similarity(smiles,?, " + minSmilarity + ", -1, 1) = 1";
        return commonSimilaritySearch(connection, query, sqlQuery);
    }

    public static List<SimilarityResult> commonSimilaritySearch(Connection connection, String query, String sqlQuery) {
        var fetcher = new SqlFetcher(connection, sqlQuery);
        fetcher.executeQuery(new Object[]{query});
        var hits = new ArrayList<SimilarityResult>();
        for (var row : fetcher) {
            var hit = new LoadSmiles.SmilesAndId(SqlFetcher.objectToString(row[1]), SqlFetcher.objectToString(row[2]));
            var similarity = SqlFetcher.objectToDouble(row[0]);
            hits.add(new SimilarityResult(hit, similarity));
        }
        fetcher.finish();
        return hits;
    }

    public static List<SimilarityResult> extendedSimilaritySearch(Connection connection, String query, String tableName,
                                                                  double minSmilarity, String fp, String method, Double alpha, Double beta) {
        var alphaString = alpha == null ? "null" : alpha.toString();
        var betaString = beta == null ? "null" : beta.toString();
        var sqlQuery = "select c$cschem1.similarityScore(1), id, smiles from " +
                tableName + " where c$cschem1.extended_similarity(smiles, '" + fp + "', '" + method + "', ?, " + minSmilarity +
                ", -1, " + alphaString + ", " + betaString + ", 1) = 1";
        return commonSimilaritySearch(connection, query, sqlQuery);
    }

    // cache fingerprints or tests will be very slow
    private static final Map<String, ExplicitBitVect> fpCache = new HashMap<>();

    public static List<SimilarityResult> similaritySearchInMemory(String query, List<LoadSmiles.SmilesAndId> structures, double minSimilarity) {
        var queryFingerprint = fpCache.computeIfAbsent(query, q -> RDKitOps.smilesToMol(q).map(RDKitOps::patternFingerPrintMol).get());

        return structures.stream()
                .map(s -> {
                    var fingerprint = fpCache.computeIfAbsent(s.getSmiles(), t -> RDKitOps.smilesToMol(t).map(RDKitOps::patternFingerPrintMol).get());
                    var similarity = RDKitOps.similarity(queryFingerprint, fingerprint);
                    return new SimilarityResult(s, similarity);
                })
                .filter(sr -> sr.getSimilarity() >= minSimilarity)
                .collect(Collectors.toList());
    }

    private static final Map<String, SparseIntVectu32> ecfp4Cache = new HashMap<>();
    private static final Map<String, SparseIntVectu32> fcfp6Cache = new HashMap<>();

    public static List<SimilarityResult> extendedSimilaritySearchInMemory(RDKitOps.ExtendedFingerPrintType fpType, String query, List<LoadSmiles.SmilesAndId> structures,
                                                             double minSimilarity, String method, Double alpha, Double beta) {
        assert fpType == RDKitOps.ExtendedFingerPrintType.ECFP4 || fpType == RDKitOps.ExtendedFingerPrintType.FCFP6;
        var cache = fpType == RDKitOps.ExtendedFingerPrintType.ECFP4 ? ecfp4Cache : fcfp6Cache;
        var queryFingerprint = cache.computeIfAbsent(query,
                q -> RDKitOps.smilesToMol(q).map(fpType::createFingerprint).get());

        return structures.stream()
                .map(s -> {
                    var fp = cache.computeIfAbsent(s.getSmiles(),
                            smi -> RDKitOps.smilesToMol(smi).map(fpType::createFingerprint).get());

                    double similarity;
                    switch (method.toLowerCase()) {
                        case "tanimoto":
                            similarity = RDKFuncs.TanimotoSimilaritySIVu32(queryFingerprint, fp);
                            break;
                        case "dice":
                            similarity = RDKFuncs.DiceSimilarity(queryFingerprint, fp);
                            break;
                        case "tversky":
                            similarity = RDKFuncs.TverskySimilarity(queryFingerprint, fp, alpha, beta);
                            break;
                        default:
                            throw new RuntimeException("Unknown similarity search method " + method);
                    }
                    return new SimilarityResult(s, similarity);
                })
                .filter(sr -> sr.getSimilarity() >= minSimilarity)
                .collect(Collectors.toList());
    }

    public static void cacheOn(Connection connection) {
        var update = "call c$cschem1.chem_structure.enableCache()";
        SqlFetcher.updateCommand(connection, update, null);
    }


    public static void cacheOff(Connection connection) {
        var update = "call c$cschem1.chem_structure.disableCache()";
        SqlFetcher.updateCommand(connection, update, null);
    }

}
