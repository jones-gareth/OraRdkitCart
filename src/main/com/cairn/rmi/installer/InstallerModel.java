package com.cairn.rmi.installer;

import com.cairn.common.*;
import com.cairn.common.RegistrySearch.RegistryEntry;
import com.cairn.rmi.util.LoadNCI;
import org.apache.commons.exec.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.*;

/**
 * A class to perform install actions.
 * <p>
 * Note different threads will access this class, but no concurrent access
 * should occur.
 *
 * @author gjones
 */
public class InstallerModel implements LoadNCI.LoadNCIListener {
    private static final String[] JAR_FILES = new String[]{"log4j-*.jar",
            "commons-lang-2*.jar",
            "oracle-wrapper-sources.jar"};
    private volatile String host = "", jdbcDatabase = "", adminPasswd = "",
            cartridgePasswd = "", rmiHost, userTablespace = "", tmpTablespace = "",
            registryOracleHome;
    private final List<InstallerMessageListener> messageListeners = new ArrayList<>();
    private static final String[] ORACLE_VERSIONS = {"12", "18", "19"};
    private final Date installDate = new Date();
    private volatile int cacheSize;
    private volatile boolean useCache = false,
            useSubstructureSearchPool = Runtime.getRuntime().availableProcessors() > 1,
            useIndexBuildThreadPool = Runtime.getRuntime().availableProcessors() > 1;
    private volatile int javaVmMemory;
    private volatile FileWriter logWriter;
    private volatile boolean buildTestIndex, loadNciCompounds;
    private volatile List<String> extraUsers;
    private volatile int nThreads = Runtime.getRuntime().availableProcessors() + 2;
    private volatile int nSubsearchThreads = Runtime.getRuntime().availableProcessors();
    private volatile int nIndexBuildThreads = Runtime.getRuntime().availableProcessors();
    private volatile int portNo = 1521;
    private volatile boolean webUrl = false;
    private volatile boolean rdsInstall = false;
    private volatile String rdsAdminUser;

    public enum InstallType {
        ALL, ORACLE, RMI
    }

    private InstallType installType = InstallType.ALL;

    private static final Logger logger = Logger.getLogger(InstallerModel.class);

    public InstallerModel() {
        super();
        // need to have this here as we can't create the install log dir if the
        // cartridge home is broken
        checkCartridgeHome();
        createInstallLogDir();
        try {
            logWriter = new FileWriter(new File(installLogDir(), "installer.log"));
        } catch (IOException e) {
            logger.error("Failed to create log dir", e);
        }

        var rdsProp = System.getProperty("cartridge.rdsInstall");
        if (rdsProp != null && rdsProp.equals("true")) {
            rdsInstall = true;
        }
        rdsAdminUser = System.getProperty("cartridge.rdsAdminUser");
        if (rdsAdminUser == null)
            rdsAdminUser = "admin";
    }

    /**
     * Determines the Oracle Home directory.
     *
     * @return
     */
    private File oracleHome() {
        String oracleHomeName = System.getenv("ORACLE_HOME");
        if (StringUtils.isNotEmpty(oracleHomeName))
            return new File(oracleHomeName);
        String path = System.getenv("PATH");
        String[] dirs = StringUtils.split(path, File.pathSeparatorChar);
        for (String dir : dirs)
            if (dir.contains("app") && dir.contains("product") && dir.contains("11")) {
                File oracleHome = new File(dir);
                return oracleHome.getParentFile();
            }
        if (getOs() == Os.WINDOWS) {
            if (registryOracleHome != null)
                return new File(registryOracleHome);
            if ((oracleHomeName = getRegistryOracleHome()) != null)
                return new File(oracleHomeName);
        }
        return null;
    }

    /**
     * Looks in the windows registry for an Oracle Home entry.
     *
     * @return
     */
    private String getRegistryOracleHome() {
        if (getOs() != Os.WINDOWS)
            return null;
        List<RegistryEntry> entries = RegistrySearch
                .getRegistryKeys("HKLM\\Software\\Oracle");

        if (entries == null) {
            System.out.println("Unable to run reg.exe");
            return null;
        }
        registryOracleHome = null;
        for (RegistryEntry entry : entries) {
            if (entry.getName().equals("ORACLE_HOME")
                    && entry.getKey().contains("home")) {
                registryOracleHome = entry.getValue();
            }
        }
        logger.info("Windows registry Oracle home is " + registryOracleHome);
        return registryOracleHome;
    }

    /**
     * Checks to see that Oracle home is set and valid.
     *
     * @return
     */
    public boolean checkOracleHome() {
        message("Checking that we can see oracle home");
        File oracleHome = oracleHome();

        if (oracleHome == null) {
            error("Oracle Home environment variable is not set and unable to determine home from path\n" +
                            "Automatic identification of Oracle databases and resources will not be possible",
                    false);
            return true;
        }
        if (!oracleHome.exists()) {
            error("Oracle Home " + oracleHome + " does not exist", true);
            return false;
        }
        if (!oracleHome.isDirectory()) {
            error("Oracle Home " + oracleHome + " is not a directory", true);
            return false;
        }
        if (!oracleHome.canRead()) {
            error("Oracle Home " + oracleHome + " is not readable", true);
            return false;
        }
        message("Oracle home found at " + oracleHome);
        return true;
    }


    /**
     * Hijack the instrumentation routines to get the available physical memory.
     *
     * @return
     * @throws ModelException
     */
    public int availableMemoryMb() throws ModelException {
        OperatingSystemMXBean operatingSystemMXBean = ManagementFactory
                .getOperatingSystemMXBean();
        Long memory = null;
        try {
            Method method = operatingSystemMXBean.getClass()
                    .getDeclaredMethod("getTotalPhysicalMemorySize");
            method.setAccessible(true);
            memory = (Long) method.invoke(operatingSystemMXBean);
        } catch (Exception e) {
            logger.error("Unable to determine physical memory", e);
        }
        if (memory == null)
            error("Unable to determine available physical memory", true);
        long memoryMB = memory / (1024L * 1024L);
        return (int) memoryMB;
    }

    public enum Arch {
        ARCH32, ARCH64
    }

    public enum Os {
        WINDOWS, LINUX
    }

    /**
     * Determine the machine architecture
     *
     * @return
     */
    private Arch getMachineArch() {
        boolean is64bit;

        if (System.getProperty("os.name").contains("Windows")) {
            is64bit = (System.getenv("ProgramFiles(x86)") != null);
        } else {
            is64bit = System.getProperty("os.arch").contains("64");
        }

        return is64bit ? Arch.ARCH64 : Arch.ARCH32;
    }

    /**
     * Determine the Java VM Architecture
     *
     * @return
     */
    public Arch getVmArch() {
        int nBits = Integer.parseInt(System.getProperty("sun.arch.data.model"));
        return nBits == 64 ? Arch.ARCH64 : nBits == 32 ? Arch.ARCH32 : null;
    }

    /**
     * @return the operating system type
     */
    public Os getOs() {
        String archProperty = System.getProperty("os.name");
        if (archProperty.equals("Linux"))
            return Os.LINUX;
        if (archProperty.startsWith("Windows"))
            return Os.WINDOWS;
        return null;
    }

    /**
     * Checks to see if the operating system is OK.
     *
     * @return
     */
    public boolean checkOs() {
        message("Checking OS.");
        Os os = getOs();
        if (os == null) {
            error("Unable to determine OS: you can only install the cartridge on Windows and Linux",
                    true);
            return false;
        }
        message("OS is ok.");
        return true;
    }

    /**
     * Checks to see that we're running Oracle 11G.
     *
     * @param connection
     * @return
     * @throws ModelException
     */
    public boolean checkOracleVersion(Connection connection) throws ModelException {
        CallableStatement stmt = null;
        try {
            message("Checking Oracle version");
            String query = "begin dbms_utility.db_version (?, ?); end;";
            stmt = connection.prepareCall(query);
            stmt.registerOutParameter(1, Types.VARCHAR);
            stmt.registerOutParameter(2, Types.VARCHAR);
            stmt.execute();
            String version = stmt.getString(1);
            boolean versionOk = false;
            for (String supportedVersion : ORACLE_VERSIONS) {
                if (version.startsWith(supportedVersion)) {
                    versionOk = true;
                }
            }
            if (!versionOk) {
                error("Version " + version
                        + " of oracle is not supported.  Please use version(s) "
                        + StringUtils.join(ORACLE_VERSIONS, ", "), true);
            }

            message("Using version " + version + " of Oracle");
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            error("Unable to determine Oracle version", true);
            return false;
        } finally {
            try {
                stmt.close();
            } catch (Exception e) {
            }
        }
    }

    /**
     * Checks to see if a user is already present in Oracle
     *
     * @param connection
     * @param user
     * @return
     * @throws ModelException
     */
    public boolean checkUserPresent(Connection connection, String user)
            throws ModelException {
        String query = "select username from all_users where upper(username) = ?";
        Object test = SqlFetcher.fetchSingleValue(connection, query,
                new Object[]{user.toUpperCase()});
        return test != null;
    }

    /**
     * Returns a list of available table spaces.
     *
     * @param connection
     * @return
     * @throws ModelException
     */
    public List<String> retrieveTablespaces(Connection connection) throws ModelException {
        String query = "select unique(tablespace_name) from dba_data_files order by 1 ";
        return SqlFetcher.objectListToStringList(
                SqlFetcher.fetchSingleColumn(connection, query, null));
    }

    /**
     * Returns a list of available temporary table spaces.
     *
     * @param connection
     * @return
     * @throws ModelException
     */
    public List<String> retrieveTmpTablespaces(Connection connection)
            throws ModelException {
        String query = "select unique(tablespace_name) from dba_temp_files order by 1 ";
        return SqlFetcher.objectListToStringList(
                SqlFetcher.fetchSingleColumn(connection, query, null));
    }

    /**
     * Guesses possible database names by looking at directory names in the
     * oradata directory.
     *
     * @return
     */
    List<String> possibleDatabases() {
        List<String> databases = new ArrayList<>();
        String name = System.getenv("ORACLE_SID");
        if (StringUtils.isNotEmpty(name))
            databases.add(name);

        // standard oracle install
        File oracleDataDir = new File(oracleHome(),
                File.separatorChar + ".." + File.separatorChar + ".." + File.separatorChar
                        + ".." + File.separatorChar + "oradata");
        addDirectoryNames(oracleDataDir, databases);

        // oraToolkit default install
        oracleDataDir = new File(
                File.separatorChar + "data01" + File.separatorChar + "rdbms");
        addDirectoryNames(oracleDataDir, databases);

        return databases;
    }

    /**
     * Add all subdirectories to a list of possible databases.
     *
     * @param oracleDataDir
     * @param databases
     */
    private void addDirectoryNames(File oracleDataDir, List<String> databases) {
        if (oracleDataDir.exists() && oracleDataDir.isDirectory()
                && oracleDataDir.canRead()) {
            File[] directories = oracleDataDir.listFiles(File::isDirectory);
            for (File dir : directories) {
                String name = dir.getName();
                if (!databases.contains(name))
                    databases.add(name);
            }
        }
    }

    /**
     * Determines the cartridge home directory. Assumes that the installer is
     * run from the cartridge home directory.
     *
     * @return
     */
    public File cartridgeHome() {
        File userDir = new File(System.getProperty("user.dir"));
        Set<String> files = new HashSet<>(Arrays.asList(userDir.list()));
        if (files.contains("oracle_wrapper") && files.contains("rmi_server.jar")) {
            userDir = new File(userDir.getParent());
            files = new HashSet<>(Arrays.asList(userDir.list()));
        }
        if (files.contains("log4j.properties.template")
                && files.contains("server.properties.template")) {
            userDir = new File(userDir.getParent());
            files = new HashSet<>(Arrays.asList(userDir.list()));
        }
        if (files.contains("bin") && files.contains("sql")) {
            var path = Paths.get(userDir.getPath(), "bin", "server.properties.template");
            if (path.toFile().exists())
                return userDir;
        }

        return null;
    }

    /**
     * checks to see that the cartridge install directory is found.
     *
     * @return
     */
    public boolean checkCartridgeHome() {
        message("Checking that we can see the cartridge install");
        File cartridgeHome = cartridgeHome();
        if (cartridgeHome == null) {
            error("Unable to determine cartridge home directory from starting directory\n"
                    + System.getProperty("user.dir")
                    + ".\nPlease run the installer from the cartridge directory", true);
            return false;
        }
        message("Cartridge install found");
        return true;
    }

    /**
     * Gets an administration connection
     *
     * @return
     * @throws ModelException
     */
    public Connection getAdminConnection() throws ModelException {
        var user = rdsInstall ? rdsAdminUser : "sys";
        return SqlUtil.getConnection(host, jdbcDatabase, String.valueOf(portNo), user,
                adminPasswd);
    }

    /**
     * Gets an cartridge connection
     *
     * @return
     * @throws ModelException
     */
    private Connection getCartridgeConnection() throws ModelException {
        return SqlUtil.getConnection(host, jdbcDatabase, String.valueOf(portNo),
                "c$cschem1", cartridgePasswd);
    }

    /**
     * Gets an cartridge test connection
     *
     * @return
     * @throws ModelException
     */
    public Connection getTestConnection() throws ModelException {
        return SqlUtil.getConnection(host, jdbcDatabase, String.valueOf(portNo),
                "cschem1_test", cartridgePasswd);
    }

    /**
     * Tests that we can get a connection using JDBC to sys
     *
     * @return
     * @throws ModelException
     */
    public boolean testJDBCConnection() {
        message("Testing JDBC connectivity to Oracle");
        Connection connection = null;
        try {
            connection = getAdminConnection();
        } catch (ModelException e) {
            error("Unable to connect to Oracle database using JDBC given current settings",
                    false);
            return false;
        } finally {
            SqlUtil.closeConnection(connection);
        }

        message("Successfully connected to Oracle");
        return true;
    }

    public boolean updateDefaultRmiHost() {
        message("Setting default rmihostname on server to " + rmiHost);
        Connection connection = null;
        try {
            connection = getCartridgeConnection();
            String update = " update rmi_hostname set rmi_hostname = ? ";
            SqlFetcher.updateCommand(connection, update, new Object[]{rmiHost});
            connection.commit();
        } catch (ModelException | SQLException e) {
            logger.error("Exception updating RMI hostname", e);
            error("Unable to update default RMI settings", true);
            return false;
        } finally {
            SqlUtil.closeConnection(connection);
        }

        message("Successfully updated default rmi hostname");
        return true;
    }

    /**
     * Tests that we can get a connection using JDBC to the cartridge user
     *
     * @return
     * @throws ModelException
     */
    public boolean testCartridgeConnection() throws ModelException {
        message("Testing JDBC connectivity to Oracle C$CSCHEM1 user.");
        Connection connection = null;
        try {
            connection = getCartridgeConnection();
        } catch (ModelException e) {
            error("Unable to connect to Cartrige user using JDBC given current settings",
                    false);
            return false;
        } finally {
            SqlUtil.closeConnection(connection);
        }

        message("Successfully connected to Oracle as cartridge user");
        return true;
    }

    /**
     * Checks to see if the cartridge is installed on the Oracle erver.
     *
     * @return
     * @throws ModelException
     */
    public boolean cartrigeInstalledInOracle() throws ModelException {
        Connection connection = null;
        try {
            connection = getAdminConnection();
            String query = "slect username from all_users where username = 'C$CSCHEM1'";
            Object user = SqlFetcher.fetchSingleValue(connection, query, null);
            return user != null;
        } finally {
            SqlUtil.closeConnection(connection);
        }
    }

    /**
     * Checks that the java version is ok.
     *
     * @return
     */
    public boolean checkJavaVersion() {
        message("Checking java version");
        String version = System.getProperty("java.version");
        if (!version.startsWith("11.0")) {
            error("The cartridge requires Java version 11", true);
            return false;
        }
        message("Java version " + version + " is ok");

        Arch vmArch = getVmArch();
        Arch machineArch = getMachineArch();

        if (machineArch == Arch.ARCH64 && vmArch == Arch.ARCH32) {
            error("You're running 32 bit java on a 64 bit machine.\nPlease use a 64 bit Java",
                    true);
            return false;
        } else
            message("Java architecture is ok");

        return true;
    }

    public File getLinuxCommandFile() throws ModelException {
        File installDir = cartridgeHome();
        return new File(installDir,
                File.separatorChar + "bin" + File.separatorChar + "runServer.sh");
    }

    private File getWindowsCommandFile() throws ModelException {
        File installDir = cartridgeHome();
        return new File(installDir,
                File.separatorChar + "bin" + File.separatorChar + "runServer.bat");
    }

    /**
     * Creates a linux command file.
     *
     * @throws ModelException
     */
    public boolean createLinuxCommand() throws ModelException {
        File installDir = cartridgeHome();
        File runCommandFile = getLinuxCommandFile();
        message("Installing Linux command file to " + runCommandFile.toString());

        if (runCommandFile.exists()) {
            message("Removing existing command file");
            if (!runCommandFile.delete()) {
                logger.error("Failed to delete existing command file");
            }
        }
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(runCommandFile));
            String contents = "#!/bin/sh\nnohup java -Xmx"
                    + javaVmMemory + "M -Xss8M -cp '" + installDir.toString()
                    + "/target/OraRdkitCart-1.0-SNAPSHOT-all.jar:'  com.cairn.rmi.server.TaskManagerImpl "
                    + serverConfigFile() + " &\n";
            out.write(contents);
            out.close();
        } catch (IOException e) {
            logger.error("Failed to write Linux command file", e);
            error("Failed to create command file", true);
        }
        if (!runCommandFile.setExecutable(true))
            logger.error("failed to set linux command file executable");
        message("Installed Linux command file");

        return true;
    }

    /**
     * Creates a Windows command file.
     *
     * @throws ModelException
     */
    public boolean createWindowsCommand() throws ModelException {
        File installDir = cartridgeHome();
        File runCommandFile = getWindowsCommandFile();
        message("Installing Windows command file to " + runCommandFile.toString());

        if (runCommandFile.exists()) {
            message("Removing existing command file");
            if (!runCommandFile.delete()) {
                logger.error("Failed to delete existing command file");
            }
        }
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(runCommandFile));
            String contents = "start java.exe -Xmx" + javaVmMemory + "M -Xss8M -cp \""
                    + installDir.toString()
                    + "\\target\\OraRdkitCart-1.0-SNAPSHOT-all.jar;\"  com.cairn.rmi.server.TaskManagerImpl "
                    + serverConfigFile() + "\n";
            out.write(contents);
            out.close();
        } catch (IOException e) {
            logger.error("Failed to write Windows command file", e);
            error("Failed to create command file", true);
        }
        if (!runCommandFile.setExecutable(true))
            logger.error("failed to set Windows command file executable");
        message("Installed Windows command file");

        return true;
    }

    /**
     * Starts the RMI server using the Linux command file
     *
     * @throws ModelException
     */
    public boolean runLinuxServer() throws ModelException {

        // The process will not execute when debugging in Intellij IDEA

        File command = getLinuxCommandFile();
        message("Starting linux server: running " + command.toString());

        CommandLine commandLine = CommandLine.parse("/bin/sh");
        commandLine.addArgument(command.toString());

        DefaultExecutor executor = new DefaultExecutor();
        executor.setWorkingDirectory(new File(Util.getProjectRoot(), "bin"));

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PumpStreamHandler psh = new PumpStreamHandler(outputStream, outputStream, null);
        executor.setStreamHandler(psh);

        ExecuteResultHandler handler = new DefaultExecuteResultHandler();

        try {
            executor.execute(commandLine, handler);
        } catch (Exception e) {
            e.printStackTrace();
            throw new ModelException(e);
        }

        message("Waiting for server to start..");
        try {
            Thread.sleep(30000L);
        } catch (Exception e) {
        }

        String output = outputStream.toString();
        try {
            outputStream.close();
        } catch (IOException ex) {
            logger.warn("runLinuxServer: failed to close outputStream");
        }

        logger.debug("run linux server output: " + output);
        if (output.contains("Error")) {
            error("Failed to start linux server", true);
            return false;
        }

        if (output.contains("java.rmi.server.ExportException: Port already in use")) {
            error("There is already an RMI server running!\nPlease kill it.", false);
            return false;
        }

        // this check only works if log4j is set up to log to the console

        // if
        // (!output.contains("Started RMI service registered as TaskManager")) {
        // error("The RMI server does not seem to have started correctly",
        // true);
        // return false;
        // }

        message("Linux server started");
        return true;
    }

    /**
     * Checks to see if we can connect to the data cartridge.
     *
     * @param connection
     * @return
     * @throws ModelException
     */
    public boolean checkCartridgeWorking(Connection connection) throws ModelException {
        message("Checking connectivity to cartridge");
        String query = "select c$cschem1.chem_structure.molecularWeight(?, ?) from dual";
        String query2 = "select c$cschem1.chem_structure.canonicalizeSmiles(?, ?) from dual";

        // String query =
        // " select * from dual where c$cschem1.exactMatch( 'C(=O)O', 'O=CO',
        // -1) = 1";
        // String query2 = " select * from dual where c$cschem1.substructure"
        // +
        // "('CN1CCN(CC1)Cc2ccc3c(c2)c4c5n3CCN(C5CCC4)C(=O)C6CCCCC6','C1CCCCC1>>c1ccccc1',
        // -1) = 1";

        try {
            Object[] params = new Object[]{rmiHost,
                    "CN1CCN(CC1)Cc2ccc3c(c2)c4c5n3CCN(C5CCC4)C(=O)C6CCCCC6"};
            Object test = SqlFetcher.fetchSingleValue(connection, query, params);
            logger.debug("Result from test molecular weight is " + test);
            if (test == null) {
                error("Failed to connect to Cartridge properly", true);
                return false;
            }
            params = new Object[]{rmiHost, "Cc2nc1ccccc1o2"};
            test = SqlFetcher.fetchSingleValue(connection, query2, params);
            logger.debug("Result from test canonicalization is " + test);
            if (test == null) {
                error("Failed to connect to Cartridge properly", true);
                return false;
            }
        } catch (Exception e) {
            logger.error("Failed to run cartridge test queries", e);
            error("Failed to connect to Cartridge properly", true);
            return false;
        }
        message("Can connect to cartridge from Oracle");
        return true;

    }

    private boolean runSqlCommands(Connection connection, Map<String, String> variables, String script, boolean stopOnError) {
        SqlScriptRunner runner = new SqlScriptRunner(stopOnError, false, variables);
        var file = Paths.get(cartridgeHome().getPath(), "sql", script).toFile();
        try (var reader = new FileReader(file)) {
            runner.processScript(connection, reader);
            connection.commit();
        } catch (IOException ex) {
            error("IO error running script " + script, true);
            return false;
        } catch (SQLException ex) {
            error("Sql error running script " + script, true);
            return false;
        }

        return true;
    }

    private File jarDir() {
        return Paths.get(Util.getProjectRoot().getAbsolutePath(), "target", "alternateLocation").toFile();

    }

    private Server startJarServer() {
        var server = new Server(9100);

        var jarDir = jarDir();
        var ctx = new ServletContextHandler();
        server.setHandler(ctx);

        var defaultServlet = new DefaultServlet();
        var holderPwd = new ServletHolder("default", defaultServlet);
        holderPwd.setInitParameter("resourceBase", jarDir.getAbsolutePath());
        holderPwd.setInitParameter("dirAllowed", "true");
        ctx.addServlet(holderPwd, "/*");

        server.setHandler(ctx);
        try {
            server.start();
            //server.dump(System.err);
        } catch (Exception ex) {
            logger.error("Failed to start Jetty server", ex);
        }
        return server;
    }

    /**
     * Uses jakarta commons exec to run Oracle loadjava
     * <p>
     * The process uses the lib directory of the install as it's home directory
     * so jar file paths can be local.
     *
     * @param jarFile
     * @param src
     * @return
     * @throws ModelException
     */
    private boolean loadJavaJarfile(String jarFile, boolean src) throws ModelException {
        var extra = src ? "-r " : "";
        var base = "http://" + getRmiHost() + ":9100/";
        var uri = base + jarFile;
        var command = "call dbms_java.loadjava('-f -v " + extra + uri + "')";
        logger.info("Running load java command: "+command);
         var connection = getCartridgeConnection();
        try (
            var statement = connection.prepareStatement(command)) {
            statement.execute();
            connection.commit();
        } catch (SQLException ex) {
            var msg = "Failed to load jar file: " + jarFile +". Is webserver at "+base+" accessible on Oracle host?";
            logger.error(msg, ex);
            var userErrors = userErrors(connection);
            logger.warn("User errors: "+userErrors);
            if (rdsInstall) {
                // For Oracle RDS return true here and rebuild classes by hand
                SqlUtil.commitConnection(connection);
                return true;
            } else {
                error(msg, true);
                return false;
            }
        }
        finally {
            SqlUtil.closeConnection(connection);
        }
        return true;
    }

    private String userErrors(Connection connection) {
        var query = "select text from user_errors order by line";
        var res = SqlFetcher.fetchSingleColumn(connection, query, null);
        return String.join("\n", SqlFetcher.objectListToStringList(res));
    }

    /**
     * Loads all required jar files into the oracle jvm
     *
     * @return
     * @throws ModelException
     */
    public boolean loadJarFiles() throws ModelException {
        var server = startJarServer();
        var dir = jarDir();
        for (String jarFile : JAR_FILES) {
            var fileFilter = (FileFilter) new WildcardFileFilter(jarFile);
            var matchingFiles = dir.listFiles(fileFilter);
            if (matchingFiles == null || matchingFiles.length == 0) {
                error("No match for jar file " + jarFile + " present. Run \"mvn install\".", true);
                return false;
            }
            var matchingFile = matchingFiles[0].getName();
            message("loading jar file " + matchingFile + " to Oracle JVM");
            var src = matchingFile.contains("sources");
            if (!loadJavaJarfile(matchingFile, src))
                return false;
        }
        try {
            if (!rdsInstall) {
                server.stop();
            }
        } catch (Exception ex) {
            logger.warn("Failed to stop Jetty jar file server", ex);
        }
        return true;
    }

    /**
     * Create the install log directory
     */
    private void createInstallLogDir() {
        File filePath = installLogDir();
        logger.info("creating install log directory at " + filePath);
        if (!filePath.mkdirs()) {
            logger.error("Unable to create install log directory at " + filePath);
        }
    }

    /**
     * @return the install log directory
     */
    private File installLogDir() {
        return new File(cartridgeHome(),
                File.separatorChar + "log" + File.separatorChar + "install"
                        + File.separatorChar
                        + installDate.toString().replace(' ', '_').replace(':', '.'));
    }

    /**
     * Creates cartridge users.
     *
     * @return
     * @throws ModelException
     */
    public boolean createUsers() throws ModelException {

        message("This may take some time.  Creating C$CSCHEM1 and CSCHEM1_TEST users (existing users will be dropped).");

        var sqlVariables = Map.of("varPasswd", cartridgePasswd, "varUserTS", userTablespace, "varTempTS", tmpTablespace);
        var connection = getAdminConnection();
        if (!dropUser("C$CSCHEM1"))
            return false;
        if (!dropUser("CSCHEM1_TEST"))
            return false;
        var ok = runSqlCommands(connection, sqlVariables, "permissions.sql", true);
        if (!ok)
            error("The script permissions.sql failed.\n"
                    + "Please check console messages for SQL errors", true);
        else {
            SqlUtil.commitConnection(connection);
            message("C$CSCHEM1 and CSCHEM1_TEST users created.");
        }
        SqlUtil.closeConnection(connection);
        cleanCache();
        return ok;
    }

    private void cleanCache() {
        var cacheDir = new File(cartridgeHome(), "cache");
        if (cacheDir.exists()) {
            try {
                FileUtils.deleteDirectory(cacheDir);
            } catch (IOException ex) {
                var message = "Failed to remove cache directory "+cacheDir;
                logger.error(message, ex);
                error(message, true);
            }
        }
    }

    private boolean dropUser(String userName) {
        var query = "select username from all_users where username = ?";
        var connection = getAdminConnection();
        var present = SqlFetcher.fetchSingleValue(connection, query, new Object[]{userName.toUpperCase()});
        if (present != null) {
            var update = "drop user " + userName + " cascade";
            try {
                logger.info("Dropping user " + userName);
                SqlFetcher.updateCommand(connection, update, null);
                logger.info("User dropped");
            } catch (ModelException ex) {
                var message = "Unable to drop user " + userName;
                logger.error(message, ex);
                error(message, true);
                return false;
            }
        }
        SqlUtil.closeConnection(connection);
        return true;
    }

    /**
     * Runs the chem_structure.sql script to load cartridge SSQL and PL/SQL.
     *
     * @return
     * @throws ModelException
     */
    public boolean loadCartridge() throws ModelException {

        message("This may take some time.  Loading cartridge SQL and PL/SQL");

        var connection = getCartridgeConnection();
        var sqlVariables = Map.of("rmiHost", rmiHost);
        var scripts = new String[]{"chem_structure_start.sql", "chem_structure_pkg.sql", "chem_structure_pkg_body.sql", "chem_structure_end.sql"};
        var packages = new String[]{"index_common", "graphsim_util", "index_util", "chem_structure"};
        var types = new String[]{"index_base_obj", "structure_ind_obj"};
        for (var script : scripts) {
            var ok = runSqlCommands(connection, sqlVariables, script, true);
            if (!ok) {
                error("The script " + script + " failed.\n"
                        + "Please check console messages for SQL errors", true);
                return false;
            }
            if (script.equals("chem_structure_pkg.sql") || script.equals("chem_structure_pkg_body.sql")) {
                var suffix = script.equals("chem_structure_pkg_body.sql") ? " body" : "";
                for (var pkg : packages) {
                    if (!checkPackageErrors(connection, pkg, "package" + suffix)) {
                        return false;
                    }
                }
                for (var type : types) {
                    if (!checkPackageErrors(connection, type, "type" + suffix)) {
                        return false;
                    }
                }
            }
        }

        SqlUtil.commitConnection(connection);
        message("Cartridge SQL and PL/SQL loaded");
        SqlUtil.closeConnection(connection);
        return true;
    }

    private boolean checkPackageErrors(Connection connection, String packageName, String typeName) {
        String query = "select line, text from user_errors where name = ? and type = ?";
        var results = SqlFetcher.fetchSingleRow(connection, query, new Object[]{packageName.toUpperCase(), typeName.toUpperCase()});
        if (results != null) {
            var line = SqlFetcher.objectToInt(results[0]);
            var text = SqlFetcher.objectToString(results[1]);
            String message = "Errors in " + typeName + " for " + packageName + " : " + text + " at " + line;
            logger.error(message);
            error(message, true);
            return false;
        } else {
            logger.info("No errors in " + typeName + " for " + packageName);
        }
        return true;
    }

    /**
     * Grants permissions to all the extra users
     *
     * @throws ModelException
     */
    public boolean grantExtraUsers() throws ModelException {
        for (String user : extraUsers)
            if (!grantCartridge(user)) {
                return false;
            }
        return true;
    }

    /**
     * Grants cartridge permissions to a user
     *
     * @param user
     * @return
     * @throws ModelException
     */
    public boolean grantCartridge(String user) throws ModelException {
        message("Granting cartridge permissions to " + user);
        var sqlVariables = Map.of("varUser", user);
        var connection = getCartridgeConnection();
        var ok = runSqlCommands(connection, sqlVariables, "grants.sql", true);
        if (!ok)
            error("The script grants.sql failed.\n"
                    + "Please check console messages for SQL errors", true);
        else {
            SqlUtil.commitConnection(connection);
            message("Granted cartridge permissions to " + user);
        }
        SqlUtil.closeConnection(connection);
        return ok;
    }

    /**
     * Returns a list of available Oracle users.
     *
     * @param connection
     * @return
     * @throws ModelException
     */
    public List<String> availableOracleUsers(Connection connection)
            throws ModelException {
        String query = "select username from all_users order by username";
        List<String> extraUsers = SqlFetcher.objectListToStringList(
                SqlFetcher.fetchSingleColumn(connection, query, null));
        for (String user : new ArrayList<>(extraUsers)) {
            if (user.startsWith("SYS") || user.equals("C$CSCHEM1")
                    || user.equals("CSCHEM1_TEST"))
                extraUsers.remove(user);
        }
        return extraUsers;
    }

    /**
     * Creates a new configuration file from a template.
     * <p>
     * Creates rmi service configuration file and log4j properties files from
     * templates.
     *
     * @return
     * @throws ModelException
     */
    public boolean createServerConfig() throws ModelException {
        message("Creating RMI service configuration file");
        File cartridgeHome = cartridgeHome();
        File file = new File(cartridgeHome,
                File.separator + "bin" + File.separator + "server.properties.template");
        String config;
        try {
            config = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ModelException(e);
        }
        config = config.replace("<use_cache>", String.valueOf(useCache))
                .replace("<cache_size>", String.valueOf(cacheSize))
                .replace("<rmi_hostname>", rmiHost).replace("<password>", cartridgePasswd)
                .replace("<test_password>", cartridgePasswd)
                .replace("<database>", jdbcDatabase).replace("<database_host>", host)
                .replace("<install_directory>",
                        cartridgeHome.toString().replace(File.separatorChar, '/'))
                .replace("<port_no>", String.valueOf(portNo))
                .replace("<n_threads>", String.valueOf(nThreads))
                .replace("<use_substructure_search_thread_pool>",
                String.valueOf(useSubstructureSearchPool))
                .replace("<n_substructure_search_threads>",
                        String.valueOf(nSubsearchThreads))
                .replace("<use_index_build_thread_pool>",
                        String.valueOf(useIndexBuildThreadPool))
                .replace("<n_index_build_threads>", String.valueOf(nIndexBuildThreads));
        String configFileName = serverConfigFile();
        File configFile = new File(configFileName);
        if (configFile.exists()) {
            message("Deleting existing configuration file " + configFileName);
            if (!configFile.delete())
                logger.error("Failed to delete existing config file!");
        }
        try {
            FileUtils.writeStringToFile(configFile, config, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ModelException(e);
        }
        message("Created RMI service configuration file");

        message("Creating log4j configuration file");
        String logFile = cartridgeHome.toString() + "/log/rmi/rmi_server.log";
        file = new File(cartridgeHome,
                File.separator + "bin" + File.separator + "log4j.properties.template");
        String log4j;
        try {
            log4j = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ModelException(e);
        }
        log4j = log4j.replace("<log_file>", logFile.replace(File.separatorChar, '/'));
        File log4jFile = new File(cartridgeHome,
                File.separator + "bin" + File.separator + "log4j.properties");
        if (log4jFile.exists()) {
            message("Deleting existing log4j file " + log4jFile.toString());
            if (!log4jFile.delete())
                logger.error("Failed to delete existing log4j file!");
        }
        try {
            FileUtils.writeStringToFile(log4jFile, log4j, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ModelException(e);
        }
        message("Created log4j configuration file");

        return true;
    }

    /**
     * @return The path to the server configuration filename.
     */
    public String serverConfigFile() {
        // File configFile = new File(cartridgeHome(), File.separator + "bin"
        // + File.separator + "server.properties." + rmiHost);
        File configFile = new File(cartridgeHome(),
                File.separator + "bin" + File.separator + "server.properties");
        return configFile.toString();
    }

    /**
     * Use Apache procrun to install the rmiserver as a Windows service.
     *
     * @return
     * @throws ModelException
     */
    public boolean installService() throws ModelException {
        message("Installing RMI server as windows service");
        File cartridgeHome = cartridgeHome();
        File binDir = new File(cartridgeHome, "bin");
        File serviceFile = null;
        File monitorCommand = new File(binDir, "cartridgeServerW.exe");

        if (getVmArch() == Arch.ARCH64) {
            message("Using 64 bit version of procrun");
            serviceFile = new File(binDir, "cartridgeService64.exe");
        } else if (getVmArch() == Arch.ARCH32) {
            message("Using 32 bit version of procrun");
            serviceFile = new File(binDir, "cartridgeService32.exe");
        }
        if (serviceFile != null)
            serviceFile.setExecutable(true);

        message("Deleting any existing service");
        try {
            runCommand(serviceFile, new String[]{"//DS//cartridgeService"}, null);
            message("Removed existing service");
        } catch (ModelException e) {
            // Ok if this fails as it probably means that there is no existing
            // service
            message("There does not appear to be any existing service");
        }
        message("Deleting any existing monitors");
        try {
            runCommand(monitorCommand, new String[]{"//MQ//cartridgeService"}, null);
            message("Removed existing service monitor");
        } catch (ModelException e) {
            // Ok if this fails as it probably means that there is no existing
            // service monitor
            message("There does not appear to be any existing service monitor");
        }

        message("Creating new service");
        String logDir = new File(cartridgeHome, "log" + File.separatorChar + "procrun")
                .toString();
        String[] args = {"//IS//cartridgeService",
                "--Install=\"" + serviceFile.toString() + "\"",
                "--Description=\"RMI Structure Oracle Data Cartridge\"", "--Jvm=auto",
                "--JvmSs=8192", "--JvmMx=" + javaVmMemory,
                "--Classpath=\"" + cartridgeHome.toString() + "\\lib\\*\"",
                "--StartMode=jvm",
                "--StartClass=com.cairn.rmi.server.TaskManagerImpl",
                "--StartMethod=main", "--StartParams=\"" + serverConfigFile() + "\"",
                "--StopMode=jvm", "--StopClass=com.cairn.rmi.server.TaskManagerImpl",
                "--StopMethod=main", "--StopParams=stop", "--LogPath=\"" + logDir + "\"",
                "--Startup=auto", "--StdOutput=auto", "--StdError=auto",
                "--PidFile=service.pid"};
        File startServiceCommand = new File(binDir, "installService.bat");
        if (startServiceCommand.exists()) {
            if (!startServiceCommand.delete())
                logger.warn("unable to delete " + startServiceCommand.getPath());
        }
        try {
            FileUtils.writeStringToFile(startServiceCommand,
                    serviceFile.toString() + " " + StringUtils.join(args, " ") + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ModelException(e);
        }
        serviceFile.setExecutable(true);

        // runCommand(serviceFile, args, null);
        runCommand(startServiceCommand, null, null);

        // save monitor command in batch file
        File monitorBatchCommand = new File(binDir, "monitorService.bat");
        if (monitorBatchCommand.exists()) {
            if (!monitorBatchCommand.delete())
                logger.warn("Failed to delete monitor service batch file");
        }

        try {
            FileUtils.writeStringToFile(monitorBatchCommand, "start \"\" \""
                    + monitorCommand.toString() + "\" //MS//cartridgeService\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ModelException(e);
        }

        monitorBatchCommand.setExecutable(true);

        // start monitor application
        message("Starting monitor application");

        // Don't know what happens with the monitor, but it appears to hang the
        // calling shell if it is run from a cygwin batch command- java is
        // exited but the shell will hang until the monitor exits.

        CommandLine commandLine = CommandLine.parse(monitorCommand.toString());
        commandLine.addArgument("//MR//cartridgeService");
        DefaultExecutor executor = new DefaultExecutor();
        ExecuteResultHandler handler = new DefaultExecuteResultHandler();
        try {
            executor.execute(commandLine, handler);
        } catch (Exception e) {
            e.printStackTrace();
            throw new ModelException(e);
        }

        message("Monitor application started.  It is minimized in the system tray");

        message("Waiting for service to start");
        try {
            Thread.sleep(10000L);
        } catch (InterruptedException e) {
        }
        message("Service Started");

        return true;
    }

    /**
     * Uses apache commons exec to run a command.
     * <p>
     * Used for executing procrun.exe
     *
     * @param command
     * @param args
     * @param timeout
     * @return
     * @throws ModelException
     */
    private String runCommand(File command, String[] args, Long timeout)
            throws ModelException {
        CommandLine commandLine = CommandLine.parse(command.toString());
        if (args != null)
            for (String arg : args) {
                commandLine.addArgument(arg, false);
            }
        logger.debug("Command Line: " + commandLine.toString());
        DefaultExecutor executor = new DefaultExecutor();
        if (timeout == null)
            timeout = 60000L;
        ExecuteWatchdog watchdog = new ExecuteWatchdog(timeout);
        executor.setWatchdog(watchdog);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PumpStreamHandler psh = new PumpStreamHandler(outputStream, outputStream, null);
        executor.setStreamHandler(psh);

        try {
            int exitValue = executor.execute(commandLine);
            logger.debug("command return value is " + exitValue);
        } catch (Exception e) {
            String message = "Failed to run command " + command.toString();
            logger.error(message, e);
            throw new ModelException(message);
        }

        String contents = outputStream.toString();
        try {
            outputStream.close();
        } catch (IOException ex) {
            logger.warn("runCommand: Failed to close outputStream");
        }
        logger.debug("command output is " + contents);
        return contents;
    }

    /**
     * Listener for Installer messages.
     */
    public interface InstallerMessageListener {
        /**
         * Passes a message from the installer to the GUI
         *
         * @param message
         */
        void installerMessage(String message);

        /**
         * Passes an error from the installer to the GUI.
         *
         * @param message
         * @param fatal   Set to true if the error is fatal.
         */
        void installerError(String message, boolean fatal);
    }

    /**
     * Send an installer message to all listeners
     *
     * @param message
     */
    private void message(String message) {
        System.out.println("Message " + message);
        logMessage(message);
        for (InstallerMessageListener listener : messageListeners)
            listener.installerMessage(message);
    }

    /**
     * Write a message to the log file
     *
     * @param message
     */
    private void logMessage(String message) {
        if (!message.endsWith("\n"))
            message = message + "\n";
        try {
            if (logWriter != null) {
                logWriter.write(message);
                logWriter.flush();
            }
        } catch (IOException e) {
            logger.error("Failed to write log message", e);
        }
    }

    /**
     * Send an installer error to all listeners.
     *
     * @param message
     * @param fatal
     */
    public void error(String message, boolean fatal) {
        // System.out.println("Error " + message);
        // logMessage("Error " + message);
        message("Error: " + message);
        for (InstallerMessageListener listener : messageListeners)
            listener.installerError(message, fatal);

    }

    /*
     * (non-Javadoc)
     *
     * @see
     * com.cairn.rmi.test.LoadNCI.LoadNCIListener#loadNciProgress(java.
     * lang.String)
     */
    @Override
    public void loadNciProgress(String message) {
        message(message);
    }

    /**
     * Loads the NCI test compounds into the Cartridge Tester schema.
     *
     * @return
     */
    public boolean loadNCI() {
        try {
            message("Loading test NCI compounds into CSCHEM1_TEST schema");
            Connection connection = getTestConnection();
            File nciFile = new File(cartridgeHome(),
                    File.separator + "data" + File.separator + "NCI-Open_09-03.smi.gz");
            LoadNCI loadNCI = new LoadNCI(nciFile.toString());
            loadNCI.setLoadNCIListener(this);
            loadNCI.load(connection);
            SqlUtil.commitConnection(connection);
            SqlUtil.closeConnection(connection);
        } catch (Exception e) {
            logger.error("Failed to load NCI compounds", e);
            error("Failed to load NCI compounds", true);
            return false;
        }
        message("Finished loading NCI compounds");
        loadNciCompounds = true;
        return true;
    }

    /**
     * Builds a domain index on the test table.
     *
     * @return
     */
    public boolean buildTestIndex() {
        try {
            String update = "create index molecules_index on nci_open(smiles)indextype is c$cschem1.structureIndexType";
            message("About to run SQL command " + update);
            message("Building domain index on NCI test compounds: this will take some time");
            message("Check log file at " + rmiLogFile() + " for progress");
            Connection connection = getTestConnection();
            SqlFetcher.updateCommand(connection, update, null);
            SqlUtil.commitConnection(connection);
            SqlUtil.closeConnection(connection);
            message("Finished building domain index");
        } catch (ModelException e) {
            logger.error("Failed to build domain index", e);
            error("Failed to build domain index", true);
            return false;
        }
        buildTestIndex = true;
        return true;
    }

    /**
     * Get the default RMI host to be used based on install type.
     *
     * @return
     */
    public String getDefaultRmiHost() {
        //if (installType == InstallType.ALL)
        //   return host;

        return getLocalHostName();
    }

    private String getLocalHostName() {
        try {
            InetAddress addr = InetAddress.getLocalHost();
            // Get hostname
            return addr.getHostName();
        } catch (UnknownHostException e) {
            return "localhost";
        }
    }

    /**
     * @return the log file for the rmi server
     */
    public File rmiLogFile() {
        return new File(cartridgeHome(), File.separatorChar + "log" + File.separatorChar
                + "rmi" + File.separatorChar + "rmi_server.log");
    }

    /**
     * Adds a message listener
     *
     * @param listener
     */
    public void addListener(InstallerMessageListener listener) {
        messageListeners.add(listener);
    }

    /**
     * Removes a listener
     *
     * @param listener
     */
    public void removeListener(InstallerMessageListener listener) {
        messageListeners.remove(listener);
    }

    /**
     * @return the host
     */
    public String getHost() {
        return host;
    }

    /**
     * @return the adminPasswd
     */
    public String getAdminPasswd() {
        return adminPasswd;
    }

    /**
     * @param host the host to set
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     * @param adminPasswd the adminPasswd to set
     */
    public void setAdminPasswd(String adminPasswd) {
        this.adminPasswd = adminPasswd;
    }

    /**
     * @return the cartridgePasswd
     */
    public String getCartridgePasswd() {
        return cartridgePasswd;
    }

    /**
     * @return the rmiHost
     */
    public String getRmiHost() {
        return rmiHost;
    }

    /**
     * @return the userTablespace
     */
    public String getUserTablespace() {
        return userTablespace;
    }

    /**
     * @return the tmpTablespace
     */
    public String getTmpTablespace() {
        return tmpTablespace;
    }

    /**
     * @param cartridgePasswd the cartridgePasswd to set
     */
    public void setCartridgePasswd(String cartridgePasswd) {
        this.cartridgePasswd = cartridgePasswd;
    }

    /**
     * @param rmiHost the rmiHost to set
     */
    public void setRmiHost(String rmiHost) {
        this.rmiHost = rmiHost;
    }

    /**
     * @param userTablespace the userTablespace to set
     */
    public void setUserTablespace(String userTablespace) {
        this.userTablespace = userTablespace;
    }

    /**
     * @param tmpTablespace the tmpTablespace to set
     */
    public void setTmpTablespace(String tmpTablespace) {
        this.tmpTablespace = tmpTablespace;
    }

    /**
     * @return the jdbcDatabase
     */
    public String getJdbcDatabase() {
        return jdbcDatabase;
    }

    /**
     * @param jdbcDatabase the jdbcDatabase to set
     */
    public void setJdbcDatabase(String jdbcDatabase) {
        this.jdbcDatabase = jdbcDatabase;
    }

    /**
     * @return the cacheSize
     */
    public int getCacheSize() {
        return cacheSize;
    }

    /**
     * @return the useCache
     */
    public boolean isUseCache() {
        return useCache;
    }

    /**
     * @return the javaVmMemory
     */
    public int getJavaVmMemory() {
        return javaVmMemory;
    }

    /**
     * @param cacheSize the cacheSize to set
     */
    public void setCacheSize(int cacheSize) {
        this.cacheSize = cacheSize;
    }

    /**
     * @param useCache the useCache to set
     */
    public void setUseCache(boolean useCache) {
        this.useCache = useCache;
    }

    /**
     * @param javaVmMemory the javaVmMemory to set
     */
    public void setJavaVmMemory(int javaVmMemory) {
        this.javaVmMemory = javaVmMemory;
    }

    /**
     * @return the buildTestIndex
     */
    public boolean isBuildTestIndex() {
        return buildTestIndex;
    }

    /**
     * @return the loadNciCompounds
     */
    public boolean isLoadNciCompounds() {
        return loadNciCompounds;
    }

    /**
     * @return the extraUsers
     */
    public List<String> getExtraUsers() {
        return extraUsers;
    }

    /**
     * @param extraUsers the extraUsers to set
     */
    public void setExtraUsers(List<String> extraUsers) {
        this.extraUsers = extraUsers;
    }

    /**
     * @return the nThreads
     */
    public int getnThreads() {
        return nThreads;
    }

    /**
     * @param nThreads the nThreads to set
     */
    public void setnThreads(int nThreads) {
        this.nThreads = nThreads;
    }

    /**
     * @return the portNo
     */
    public int getPortNo() {
        return portNo;
    }

    /**
     * @param portNo the portNo to set
     */
    public void setPortNo(int portNo) {
        this.portNo = portNo;
    }

    /**
     * @return the useSubstructureSearchPool
     */
    public boolean isUseSubstructureSearchPool() {
        return useSubstructureSearchPool;
    }

    /**
     * @return the nSubsearchThreads
     */
    public int getnSubsearchThreads() {
        return nSubsearchThreads;
    }

    /**
     * @param useSubstructureSearchPool the useSubstructureSearchPool to set
     */
    public void setUseSubstructureSearchPool(boolean useSubstructureSearchPool) {
        this.useSubstructureSearchPool = useSubstructureSearchPool;
    }

    /**
     * @param nSubsearchThreads the nSubsearchThreads to set
     */
    public void setnSubsearchThreads(int nSubsearchThreads) {
        this.nSubsearchThreads = nSubsearchThreads;
    }

    /**
     * @return the installType
     */
    public InstallType getInstallType() {
        return installType;
    }

    /**
     * @param installType the installType to set
     */
    public void setInstallType(InstallType installType) {
        this.installType = installType;
    }

    /**
     * @return the useIndexBuildThreadPool
     */
    public boolean isUseIndexBuildThreadPool() {
        return useIndexBuildThreadPool;
    }

    /**
     * @return the nIndexBuildThreads
     */
    public int getnIndexBuildThreads() {
        return nIndexBuildThreads;
    }

    /**
     * @param useIndexBuildThreadPool the useIndexBuildThreadPool to set
     */
    public void setUseIndexBuildThreadPool(boolean useIndexBuildThreadPool) {
        this.useIndexBuildThreadPool = useIndexBuildThreadPool;
    }

    /**
     * @param nIndexBuildThreads the nIndexBuildThreads to set
     */
    public void setnIndexBuildThreads(int nIndexBuildThreads) {
        this.nIndexBuildThreads = nIndexBuildThreads;
    }

}
