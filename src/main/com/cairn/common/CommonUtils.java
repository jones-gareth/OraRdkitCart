package com.cairn.common;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

/**
 * Miscellaneous utilities.
 *
 * @author gjones
 */
public class CommonUtils {
    private static final Logger logger = Logger.getLogger(CommonUtils.class);

    private CommonUtils() {
    }

    private static final AtomicInteger counter = new AtomicInteger(0);

    /**
     * @return a unique identifier
     */
    public static String getUniqLabel() {
        String javaName = getJavaName();
        long nSecs = (new Date()).getTime();
        String uniq = javaName + '_' + nSecs + '_' +
                counter.incrementAndGet();
        return uniq.replace('@', '_');
    }

    /**
     * @return a unique name for the JVM process- closest thing to a Java
     * getPID.
     */
    public static String getJavaName() {
        return ManagementFactory.getRuntimeMXBean().getName();
    }

    /**
     * Return a stack trace as a string
     *
     * @param aThrowable
     * @return An exception stack trace
     */
    public static String getStackTrace(Throwable aThrowable) {
        try (Writer result = new StringWriter();
             PrintWriter printWriter = new PrintWriter(result)) {
            aThrowable.printStackTrace(printWriter);
            return aThrowable.getMessage() + " : " + result.toString();
        } catch (IOException ex) {
            return "Unable to create stacktrace: IOException";
        }
    }

    /**
     * Reads in the contents of a resource file and returns it as a string.
     *
     * @param clazz
     * @param resource
     * @return
     */
    public static String resourceToString(Class<?> clazz, String resource) {
        try (var in = new BufferedReader(new InputStreamReader(clazz.getResourceAsStream(resource)))) {
            String file = StringUtils.join(IOUtils.readLines(in), '\n');
            in.close();
            return file;
        } catch (IOException e) {
            String message = "IOException reading resource " + resource;
            logger.error(message, e);
            throw new RuntimeException(message, e);
        }
    }

    /**
     * Saves a java object to a file.
     *
     * @param file
     * @param object
     */
    public static void objectToFile(File file, Object object) {
        try {
            if (file.exists()) {
                if (!file.delete())
                    logger.warn("ObjectToFile: failed to delete existing object file "
                            + file);
            }

            BufferedOutputStream fos = new BufferedOutputStream(
                    new FileOutputStream(file));
            // FileOutputStream fos = new FileOutputStream(indexFile);
            ObjectOutputStream out = new ObjectOutputStream(fos);
            out.writeObject(object);
            out.close();
        } catch (IOException e) {
            String message = "ObjectToFile: IOException saving object to file "
                    + file;
            logger.error(message);
            throw new RuntimeException(message);
        }
        logger.debug("ObjectToFile: Saved object to file " + file);
    }

    /**
     * Converts a file which holds a single serializable object back to that
     * object.
     *
     * @param file
     * @return
     */
    public static Object fileToObject(File file) {
        if (!file.exists())
            throw new RuntimeException("FileToObject: no file present at "
                    + file.getPath());
        try (var fis = new BufferedInputStream(new FileInputStream(file));
             var in = new ObjectInputStream(fis)) {
            var object = in.readObject();
            logger.debug("FileToObject: Loaded object from file " + file);
            return object;
        } catch (IOException e) {
            var message = "FileToObject: IOException on object file read "
                    + e;
            logger.error(message, e);
            throw new RuntimeException(message);
        } catch (ClassNotFoundException e) {
            var message = "FileToObject: Class not found on object file read "
                    + e;
            logger.error(message, e);
            throw new RuntimeException(message);
        }
    }

    /**
     * Safely convert a long to an int value.
     *
     * @param value
     * @return
     */
    public static int longToInt(long value) {
        if (value > Integer.MAX_VALUE || value < Integer.MIN_VALUE)
            throw new IllegalArgumentException("value " + value
                    + " cannot be converted to int without loss of data");
        return (int) value;
    }
}
