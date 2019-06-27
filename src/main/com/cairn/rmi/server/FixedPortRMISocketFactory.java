package com.cairn.rmi.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.server.RMISocketFactory;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * Factory class for RMI server sockets. This ensures that the second server
 * socket created by the RMI server is the first open port greater or equal to
 * 1098. This makes using RMI with firewalls possible.
 * 
 * @author Gareth Jones
 *
 */
class FixedPortRMISocketFactory extends RMISocketFactory {
	private static final int RMI_PORT = 1098;

	private static final Logger logger = Logger
			.getLogger(FixedPortRMISocketFactory.class);

	@Override
	public Socket createSocket(String host, int port) throws IOException {
		logger.debug("Creating socket to host : " + host + " on port " + port);
		return new Socket(host, port);
	}

	@Override
	public ServerSocket createServerSocket(int port) throws IOException {
		logger.debug("Request to create ServerSocket on port " + port);
		if (port == 0) {
			port = RMI_PORT;
			while (!portAvailable(port)) {
				port++;
			}
		}
		logger.info("Creating ServerSocket on port " + port);
		return new ServerSocket(port);
	}

	/**
	 * Return true if this port is available
	 * 
	 * @param port
	 * @return
	 */
	private boolean portAvailable(int port) {
		try {
			new ServerSocket(port).close();
			return true;
		} catch (IOException e) {
			return false;
		}
	}
}
