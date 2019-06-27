package com.cairn.common;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A class to get registry entries by parsing the output from REG.EXE
 * 
 * @author gjones
 * 
 */
public class RegistrySearch {

	private static final Logger logger = Logger.getLogger(RegistrySearch.class);

	/**
	 * A class to store registry key entries
	 */
	public static class RegistryEntry {
		private final String key, name, value;

		private RegistryEntry(String key, String name, String value) {
			super();
			this.key = key;
			this.name = name;
			this.value = value;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			return "Key " + key + " name " + name + " value " + value;
		}

		/**
		 * @return the key
		 */
		public String getKey() {
			return key;
		}

		/**
		 * @return the name
		 */
		public String getName() {
			return name;
		}

		/**
		 * @return the value
		 */
		public String getValue() {
			return value;
		}

	}

	/**
	 * Retrieves all registry keys beneath this key
	 * 
	 * @param registryKey
	 * @return
	 */
	public static List<RegistryEntry> getRegistryKeys(String registryKey) {

		String line = "reg.exe";
		CommandLine cmdLine = CommandLine.parse(line);
		cmdLine.addArgument("query");
		cmdLine.addArgument(registryKey);
		cmdLine.addArgument("/s");
		DefaultExecutor executor = new DefaultExecutor();

		String contents;
		try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
		PumpStreamHandler psh = new PumpStreamHandler(outputStream, outputStream, null);
			executor.setStreamHandler(psh);

			try {
				int exitValue = executor.execute(cmdLine);
				logger.info("Reg.exe return value is " + exitValue);
			} catch (ExecuteException e) {
				logger.error("Execute exception running reg.exe " + e);
				return null;
			} catch (IOException e) {
				logger.error("IOException execution running reg.exe " + e);
				return null;
			}

			contents = outputStream.toString();
			logger.debug("Reg.exe" + contents);
		} catch (IOException ex) {
			throw new RuntimeException("IOException retrieving registry keys");
		}

		try {
			return parseRegistryText(contents);
		} catch (Exception e) {
			logger.info("failed to parse reg output");
		}
		return null;
	}

	private static List<RegistryEntry> parseRegistryText(String text) {
		List<RegistryEntry> entries = new ArrayList<>();

		Pattern pattern = Pattern.compile("^\\s+(\\w+)\\s+REG_\\w+\\s+(\\w.*)$");
		String[] lines = StringUtils.split(text, "\n");
		String currentKey = null;
		for (String line : lines) {
			logger.debug("processing line " + line);
			line = line.replace("\r", "");
			if (StringUtils.isEmpty("line"))
				continue;
			if (line.startsWith("!"))
				continue;

			if (!line.startsWith(" ")) {
				currentKey = line;
				continue;
			}

			if (StringUtils.isBlank(line))
				continue;

			Matcher matcher = pattern.matcher(line);
			if (!matcher.matches()) {
				String message = "Failed to match registry line " + line;
				logger.error(message);
				continue;
			}
			String name = matcher.group(1);
			String value = matcher.group(2);

			if (StringUtils.isEmpty(currentKey)) {
				logger.warn("Current key is not defined");
				continue;
			}
			logger.debug("Adding entry " + currentKey + " name " + name + " value "
					+ value);
			RegistryEntry entry = new RegistryEntry(currentKey, name, value);
			entries.add(entry);
		}

		return entries;
	}

}
