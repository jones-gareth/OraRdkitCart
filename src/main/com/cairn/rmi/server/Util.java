package com.cairn.rmi.server;

import java.io.*;

import org.apache.log4j.Logger;

/**
 *
 * @author Gareth Jones
 */
public class Util {
    private static final Logger logger = Logger.getLogger(Util.class);

    /**
     * Prints Java Memory
     */
    public static void printMemoryUsage(Logger logger) {
        Runtime runtime = Runtime.getRuntime();
        long maxMem = runtime.maxMemory();
        long totalMem = runtime.totalMemory();
        long freeMem = runtime.freeMemory();

        long dem = 1024 * 1024;
        maxMem /= dem;
        totalMem /= dem;
        freeMem /= dem;
        long currentMem = totalMem - freeMem;
        logger.info("Max Memory (MB) " + maxMem + " Total " + totalMem
                + " Free " + freeMem + " Current " + currentMem);
    }

    /**
     * Converts an object to a byte array, using serialization.
     *
     * @param object
     * @return
     * @throws IOException
     */
    public static byte[] objectToByteArray(Object object) {
        try (var bos = new ByteArrayOutputStream();
             var out = new ObjectOutputStream(bos)) {
            out.writeObject(object);
            return bos.toByteArray();
        } catch (IOException ex) {
            var msg = "Failed to serialize object!";
            logger.error(msg, ex);
            throw new RuntimeException(msg, ex);
        }
    }

    /**
     * Converts a byte array to an object, by de-serialization
     *
     * @param data
     * @return
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public static Object byteArrayToObject(byte[] data) {
        try (var bis = new ByteArrayInputStream(data);
             var in = new ObjectInputStream(bis)) {
            return in.readObject();
        } catch (IOException | ClassNotFoundException ex) {
            var msg = "Failed to  object!";
            logger.error(msg, ex);
            throw new RuntimeException(msg, ex);
        }
    }


}
