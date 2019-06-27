package com.cairn.rmi.util;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.zip.GZIPInputStream;

/**
 * Class to load NCI Open 250K database to CSCHEM1_TEST user.
 * 
 * @author Gareth Jones
 * 
 */
public class LoadNCI {
	private final String fileName;
	private LoadNCIListener loadNCIListener;

	public interface LoadNCIListener {
		void loadNciProgress(String message);
	}

	private void message(String message) {
		if (loadNCIListener != null)
			loadNCIListener.loadNciProgress(message);
		else
			System.out.println(message);
	}

	public LoadNCI(String fileName) {
		this.fileName = fileName;
	}

	public static void main(String[] args) {
		if (args.length < 2) {
			System.out.println("Usage: " + LoadNCI.class.getName()
					+ " <database> <password> [<file> <host> <port>]");
			return;
		}
		String database = args[0];
		String password = args[1];
		String file = args.length > 2 ? args[2] : "NCI-Open_09-03.smi.gz";
		String host = args.length > 3 ? args[3] : "localhost";
		String port = args.length > 4 ? args[4] : "1521";

		try {
			DriverManager.registerDriver(new oracle.jdbc.OracleDriver());

			String connStr = "jdbc:oracle:thin:@" + host + ":" + port + ":" + database;

			Connection connection = DriverManager.getConnection(connStr, "cschem1_test",
					password);
			connection.setAutoCommit(true);
			LoadNCI loadNCI = new LoadNCI(file);
			loadNCI.load(connection);
			connection.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	/**
	 * Loads the compounds.
	 * 
	 * @throws Exception
	 */
	public void load(Connection connection) throws Exception {

		System.out.println("Creating nci_open test table");
		PreparedStatement statement = null;

		try {
			statement = connection.prepareStatement("drop table nci_open");
			statement.execute();
		} catch (SQLException e) {
		} finally {
			statement.close();
		}

		try {
			statement = connection.prepareStatement("create table nci_open "
					+ "(id number primary key, smiles varchar2(1000))");
			statement.execute();
		} finally {
			statement.close();
		}

		BufferedReader in = null;
		try {
			GZIPInputStream zipStream = new GZIPInputStream(new FileInputStream(fileName));
			in = new BufferedReader(new InputStreamReader(zipStream));

			statement = connection.prepareStatement("insert into nci_open (id, smiles) "
					+ "values (:1, :2)");
			int count = 0;
			while (true) {
				String line = in.readLine();
				if (line == null)
					break;

				int pos = line.indexOf(' ');
				String smiles = line.substring(0, pos);
				String idStr = line.substring(pos + 1);

				int id = Integer.parseInt(idStr);

				statement.setInt(1, id);
				statement.setString(2, smiles);
				statement.execute();

				count++;
				if (count % 1000 == 0)
					message("loaded " + count + " compounds");
			}
		} finally {
			statement.close();
			if (in != null) {
				in.close();
			}
		}
		message("Finished");
	}

	/**
	 * @return the loadNCIListener
	 */
	public LoadNCIListener getLoadNCIListener() {
		return loadNCIListener;
	}

	/**
	 * @param loadNCIListener
	 *            the loadNCIListener to set
	 */
	public void setLoadNCIListener(LoadNCIListener loadNCIListener) {
		this.loadNCIListener = loadNCIListener;
	}

}
