package com.cairn.rmi.test.client;


import com.cairn.common.SqlFetcher;
import com.cairn.common.RDKitOps;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.SQLException;

/**
 *
 * @author Gareth Jones
 */
class TestCanonicalizeTask {

	@BeforeAll
	static void init() {
		ClientUtil.setup();
	}

	private static final String smi1 =  "c1ccc(cc1)C[C@H](C(=NO)O)NC(=O)N[C@]23C[C@@H]4C[C@H](C2)C[C@@H](C4)C3";
	private static final String smi2 = "CN1CCN(CC1)Cc2ccc3c(c2)c4c5n3CCN(C5CCC4)C(=O)C6CCCCC6";

	@ParameterizedTest
	@ValueSource(strings = {smi1, smi2})
	void testCanonicalizeSmiles(String smiles) throws SQLException {

	    var localCanSmi = RDKitOps.canonicalize(smiles).get();
	    try (var connection = ClientUtil.getTestConnection()) {
			var query = "select c$cschem1.chem_structure.canonicalizeSmiles(?) from dual";
			var cartCanSmi = SqlFetcher.objectToString(SqlFetcher.fetchSingleValue(connection, query, new Object[]{smiles}));
			assertEquals(localCanSmi, cartCanSmi, () -> "Mismatched canonicalization for "+smiles);
		}

	}

}
