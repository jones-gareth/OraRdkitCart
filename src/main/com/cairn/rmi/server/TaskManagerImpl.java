package com.cairn.rmi.server;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMISocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.sql.Connection;
import java.util.Properties;

import com.cairn.common.Util;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import com.cairn.rmi.TaskException;
import com.cairn.rmi.TaskInterface;
import com.cairn.rmi.TaskManagerInterface;
import com.cairn.rmi.index.IndexBuildPool;
import com.cairn.rmi.index.SubstructureSearchPool;
import com.cairn.rmi.index.TableIndex;
import com.cairn.rmi.task.TableIndexTask;
import com.cairn.common.MoleculeCache;

/**
 * Implements the task manager interface as an RMI server. See add-stub.sh for
 * commands to build stubs classes
 *
 * @author Gareth Jones
 */
public class TaskManagerImpl extends UnicastRemoteObject implements TaskManagerInterface {
// code for dymanic stub:
//public class TaskManagerImpl implements TaskManagerInterface {

    // application version number
    private static final String VERSION = "0.9.6.a";

    private static final Logger logger = Logger
            .getLogger(TaskManagerImpl.class.getName());

    /**
     * Number of threads in the task and batch executor pools.
     */
    private static int nThreads = 4;

    /**
     * Eclipse auto-generated version ID
     */
    private static final long serialVersionUID = 1000L;

    private TaskManagerImpl() throws RemoteException {
        // remove port argument for dynamic hub
        super(TaskManagerInterface.PORT);
    }

    private static volatile String installDir;

    private static volatile Properties properties = null;

    // These methods implement TaskManagerInterface

    /*
     * (non-Javadoc)
     *
     * @see
     * com.cairn.rmi.TaskManagerInterface#submitTask(com.cairn.rmi
     * .TaskInterface, java.lang.Object)
     */
    @Override
    public Object submitTask(TaskInterface task, Object settings) throws RemoteException,
            TaskException {
        logger.info("submitting task for task " + task.getClass().getName());
        return task.submit(settings);
    }

    /*
     * (non-Javadoc)
     *
     * @see com.cairn.rmi.TaskManagerInterface#submitTask(java.lang.String,
     * java.lang.Object)
     */
    @Override
    public Object submitTask(String className, Object settings) throws RemoteException,
            TaskException {

        // this is the main entry point for all client requests. Make sure we
        // log any error on the server, otherwise only the client will see
        // errors.
        try {
            logger.info("creating and submitting task for task " + className);
            TaskInterface task = getTask(className);
            return task.submit(settings);
        } catch (RuntimeException e) {
            logger.error("Runtime exception submitting task " + className, e);
            throw e;
        } catch (Error e) {
            logger.error("Error submitting task " + className, e);
            throw e;
        } catch (TaskException e) {
            logger.error("Task exception submitting task " + className, e);
            throw e;
        }
    }

    /**
     * Creates a task of the given class name
     *
     * @param className
     * @return
     * @throws RemoteException
     * @throws TaskException
     */
    private static TaskInterface getTask(String className) throws RemoteException,
            TaskException {
        Class<?> cls;
        TaskInterface task;

        logger.info("creating task " + className);

        try {
            // get task class
            cls = Class.forName(className);
        } catch (ClassNotFoundException ex) {
            throw new TaskException("getTask: can't find class:", ex);
        }

        try {
            // create a new task
            task = (TaskInterface) cls.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException ex) {
            ex.printStackTrace();
            throw new TaskException("getTask: class creation error", ex);
        }

        return task;
    }

    public static boolean loadPropertiesFile(String propertiesFile) {
        if (propertiesFile == null)
            propertiesFile = Paths.get(Util.getProjectRoot().getAbsolutePath(), "bin", "server.properties").toString();

        // get defaults
        properties = null;
        try {
            FileReader in = new FileReader(propertiesFile);
            properties = new Properties();
            properties.load(in);
        } catch (IOException e) {
            System.err
                    .println(" Unable to load server properties file " + propertiesFile);
            return false;
        }
        return true;
    }

    public static boolean setupConnection() {


        String password = properties.getProperty("credentials.password");
        String database = properties.getProperty("credentials.database");
        String host = properties.getProperty("credentials.host");
        String portStr = properties.getProperty("credentials.port");
        int port = portStr == null ? -1 : Integer.parseInt(portStr);

        logger.info("Connecting to " + TaskUtil.USER + "@" + database + "/" + password
                + " host " + host + " port " + port);
        TaskUtil.setCredentials(password, database, host, port);
        try {
            Connection connection = TaskUtil.getConnection(false);
            TaskUtil.closeConnection(connection);
        } catch (Exception e) {
            logger.error("Unable to connect to database: check credentials", e);
            return false;
        }
        return true;
    }


    private static void setup(String propertiesFile) {

        if (!loadPropertiesFile(propertiesFile))
            return;


        installDir = properties.getProperty("cartridge.install_directory").replace('/',
                File.separatorChar);

        // Config apache log4j logger
        String log4jFile = installDir + File.separatorChar + "bin" + File.separatorChar
                + "log4j.properties";
        if (!(new File(log4jFile).exists())) {
            System.err.println("Unable to find log4j config file " + log4jFile);
            System.exit(0);
        }
        PropertyConfigurator.configure(log4jFile);
        logger.info("RMI Server version           : " + getVersion());

        String policyFile = installDir + File.separatorChar + "bin" + File.separatorChar
                + "java.policy";
        String rmiHostname = properties.getProperty("java.rmi.server.hostname");
        if (rmiHostname == null) {
            try {
                rmiHostname = InetAddress.getLocalHost().getHostName();
                logger.info("Got local hostname " + rmiHostname);
            } catch (UnknownHostException ex) {
                logger.warn("unable to get local hostname: using localhost");
                rmiHostname = "localhost";
            }
        }

        nThreads = Integer.parseInt(properties.getProperty("task_manager.n_threads"));

        System.setProperty("java.security.policy", policyFile);
        System.setProperty("java.rmi.server.hostname", rmiHostname);
        int maxThreads = 100;
        if (maxThreads < nThreads * 5)
            maxThreads = nThreads * 5;
        System.setProperty("sun.rmi.transport.tcp.maxConnectionThreads",
                String.valueOf(maxThreads));

        boolean useMoleculeCache = Boolean.parseBoolean(properties
                .getProperty("structure_search.use_molecule_cache"));
        int cacheSize = Integer.parseInt(properties
                .getProperty("molecule_cache.cache_size"));

        MoleculeCache.setUseMoleculeCache(useMoleculeCache);
        // Always create cache, so we can turn it off dynamically, especially for client testing
        MoleculeCache.createMoleculeCache(cacheSize);
        if (!setupConnection()) {
            return;
        }

        boolean useSubstructureSearchPool = Boolean.parseBoolean(properties
                .getProperty("structure_search.use_substructure_search_thread_pool"));
        int substructureSearchPoolNThreads = Integer.parseInt(properties
                .getProperty("substructure_search_thread_pool.n_threads"));
        SubstructureSearchPool.setUseSubstructureSearchPool(useSubstructureSearchPool);
        if (useSubstructureSearchPool)
            SubstructureSearchPool.setnThreads(substructureSearchPoolNThreads);

        boolean useIndexBuildPool = Boolean.parseBoolean(properties
                .getProperty("table_index.use_index_build_thread_pool"));
        int indexBuildPoolNThreads = Integer.parseInt(properties
                .getProperty("index_build_thread_pool.n_threads"));
        IndexBuildPool.setUseIndexBuildPool(useIndexBuildPool);
        IndexBuildPool.setnThreads(indexBuildPoolNThreads);

        logger.info("Thread pool size             : " + nThreads);
        logger.info("Use molecule cache           : "
                + MoleculeCache.isUseMoleculeCache());
        logger.info("Cache Size                   : " + cacheSize);
        logger.info("Use sub search thread pool   : " + useSubstructureSearchPool);
        logger.info("Sub search thread pool size  : " + substructureSearchPoolNThreads);
        logger.info("Use index build thread pool  : " + useIndexBuildPool);
        logger.info("Index build thread pool size : " + indexBuildPoolNThreads);
        logger.info("Java policy file             : "
                + System.getProperty("java.security.policy"));
        logger.info("Java rmi host                : "
                + System.getProperty("java.rmi.server.hostname"));
        if (properties.containsKey("database.use_database_appender")) {
            boolean useDatabaseAppender = Boolean.parseBoolean(properties
                    .getProperty("database.use_database_appender"));
            TaskUtil.setUseDatabaseAppender(useDatabaseAppender);
        }

        logger.info("Using database log appender  : " + TaskUtil.isUseDatabaseAppender());

        // Now we've got credentials configure JDBC logging
        TaskUtil.addDatabaseAppender();

        try {
            if (properties.containsKey("table_index.load_tables")) {
                String[] tables = properties.getProperty("table_index.load_tables")
                        .split("\\s+");
                for (String table : tables) {
                    String[] v = table.split("\\.");
                    String ownerName = v[0];
                    String tableName = v[1];
                    String columnName = v[2];
                    logger.info("Pre-loading index for " + ownerName + "." + tableName);
                    TableIndex index = TableIndexTask.getTableIndex(ownerName, tableName,
                            columnName);
                    index.loadIndex();
                    String key = "table_index.cache." + ownerName + "." + tableName + "."
                            + columnName;
                    if (properties.containsKey(key)
                            && properties.getProperty(key).equalsIgnoreCase("true")) {
                        logger.info("Loading cache for " + ownerName + "." + tableName);
                        index.addToCache();
                    }

                }
            }
        } catch (Exception e) {
            logger.fatal("Exception loading indexes " + e);
            // System.exit(0);
        }

        if (System.getSecurityManager() == null) {
            System.setSecurityManager(new SecurityManager());
        }

    }

    /**
     * Main method to run the service. If the server has multiple interfaces you
     * may need to define java.rmi.server.hostname, or set it correctly in the properties file.
     *
     * @param argv
     */
    public static void main(String[] argv) {

        try {
            if (argv.length > 0) {

                // for apache daemon service
                if (argv[0].equalsIgnoreCase("stop")) {
                    BatchSystem.stop();
                    return;
                }

                // for version information only
                if (argv[0].equalsIgnoreCase("version")) {
                    System.out.println("Version " + getVersion());
                    return;
                }
            }


            var propertiesFile = argv.length > 0 ? argv[0] : null;
            setup(propertiesFile);

            Util.loadRdkit();

            // Start the task manager RMI process.
            try {
                String name = TaskManagerInterface.NAME;
                int rmiPort = TaskManagerInterface.PORT;
                RMISocketFactory.setSocketFactory(new FixedPortRMISocketFactory());
                LocateRegistry.createRegistry(rmiPort);
                Registry registry = LocateRegistry.getRegistry(rmiPort);

                // implement task queuing
                BatchSystem.start(nThreads);

                TaskManagerImpl taskManager = new TaskManagerImpl();
                // code for dynamic stubs
                //TaskManagerInterface stub = (TaskManagerInterface)  UnicastRemoteObject.exportObject(taskManager, rmiPort);
                //registry.bind(name, stub);
                registry.rebind(name, taskManager);
                logger.info("Started RMI service registered as " + name);
            } catch (Exception exception) {
                logger.fatal("Exception starting rmi service", exception);
                exception.printStackTrace();
            }

        } catch (Throwable e) {
            logger.fatal("Fatal exception", e);
            e.printStackTrace();
        }

    }

    /**
     * @return the installDir
     */
    public static String getInstallDir() {
        return installDir;
    }

    /**
     * Gets the RMI configuration properties.
     *
     * @return the properties
     */
    public static Properties getProperties() {
        return properties;
    }

    /**
     * @return rmi server version
     */
    private static String getVersion() {
        return VERSION;
    }
}
