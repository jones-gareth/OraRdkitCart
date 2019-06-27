package com.cairn.rmi.client;

import java.net.MalformedURLException;
import java.rmi.ConnectException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import oracle.jdbc.driver.OracleDriver;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import com.cairn.rmi.TaskException;
import com.cairn.rmi.TaskManagerInterface;

/* For oracle 12 the code and referenced classes in this class need to be Java 6 compatible
   For oracle 18 Java 7 is supported.
 */

/**
 * Helper static routines to enable submitting a task on the rmi server using
 * the class name.
 * <p>
 * This class is not thead safe- but it should only be called within the oracle
 * JVM which is single threaded.
 *
 * @author Gareth Jones
 */
public class TaskProxy {

    private static final Logger logger = Logger.getLogger(TaskProxy.class);

    static {
        logger.setLevel(Level.DEBUG);
    }

    /**
     * The RMI host defaults to the value in the rmi_hostname table
     *
     * @return RMI location name
     */
    private static String getRmiName(String rmiHostName) {
        if (StringUtils.isEmpty(rmiHostName)) {
            if (System.getProperty("oracle.jserver.version") != null) {
                try {
                    // Get rmi host from Oracle table
                    Connection connection = new OracleDriver().defaultConnection();
                    Statement stmt = connection.createStatement();
                    ResultSet results = stmt
                            .executeQuery("select rmi_hostname from rmi_hostname");
                    results.next();
                    rmiHostName = results.getString(1);
                    logger.debug("Rmi host " + rmiHostName + " retrieved from DB");
                    results.close();
                    stmt.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                    logger.warn("Failed to retrieve rmi hostname from DB");
                    rmiHostName = TaskManagerInterface.DEFAULT_RMI_SERVER_HOST;
                }
            } else {
                rmiHostName = System.getProperty("rmi_server_host",
                        TaskManagerInterface.DEFAULT_RMI_SERVER_HOST);
            }
        }

        return "rmi://" + rmiHostName + ":" + TaskManagerInterface.PORT + "/"
                + TaskManagerInterface.NAME;
    }

    private static final Map<String, TaskManagerInterface> savedTaskManagers = new HashMap<String, TaskManagerInterface>();

    /**
     * @return handle to remote service.
     * @throws TaskException
     */
    private static TaskManagerInterface lookupRemote(String rmiName) throws TaskException {
        // System.setSecurityManager(new RMISecurityManager());

        if (savedTaskManagers.containsKey(rmiName))
            return savedTaskManagers.get(rmiName);

        logger.debug("Connecting to " + rmiName);

        Object obj;
        try {
            obj = Naming.lookup(rmiName);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            throw new TaskException("TaskProxy: submit malformed url " + rmiName);
        } catch (NotBoundException e) {
            e.printStackTrace();
            throw new TaskException("TaskProxy: not bound " + rmiName);
        } catch (RemoteException e) {
            e.printStackTrace();
            throw new TaskException("TaskProxy: RemoteException " + rmiName);
        }
        TaskManagerInterface savedTaskManager = (TaskManagerInterface) obj;
        savedTaskManagers.put(rmiName, savedTaskManager);
        return savedTaskManager;
    }

    /**
     * Helper methods. Submits and runs a task on the server.
     *
     * @param className server class name
     * @param settings  initialization parameters.
     * @return results
     * @see TaskManagerInterface#submitTask(String, Object)
     */
    public static Object submit(String rmiHostname, String className, Object settings)
            throws TaskException {
        Object results;
        String rmiName = getRmiName(rmiHostname);
        TaskManagerInterface taskManager = lookupRemote(rmiName);
        try {
            results = taskManager.submitTask(className, settings);
        } catch (ConnectException e) {
            // This exception can be called if the rmi server has gone down or
            // if the connection to the server has been lost. Here we reopen the
            // connection and see if we can get the method to run with a new
            // connection.
            results = retrySubmit(rmiName, className, settings);
        } catch (RemoteException e) {
            results = retrySubmit(rmiName, className, settings);
        }
        logger.debug("Submitted task on " + rmiHostname + " class " + className);

        return results;
    }

    private static Object retrySubmit(String rmiName, String className, Object settings) throws TaskException {
        savedTaskManagers.remove(rmiName);
        logger.warn("Unable to submit task.  Reconnecting to RMI server.");
        TaskManagerInterface taskManager = lookupRemote(rmiName);
        try {
            return taskManager.submitTask(className, settings);
        } catch (RemoteException e2) {
            throw new TaskException("TaskProxy: submit: RemoteException: " + e2);
        }
    }
}
