
## Issues

### RMI 

Support for stubs is deprecated, see 
[API docs](https://docs.oracle.com/javase/8/docs/api/java/rmi/server/UnicastRemoteObject.html). 
However, attempts to call exportObject from the ojvm client fail, with
an Oracle security method exception which does not make sense:

```
c$cschem1@GJDEV> select chem_structure.molecularWeight('gareth-p51', 'Cc2nc1ccccc1o2') from dual;
select chem_structure.molecularWeight('gareth-p51', 'Cc2nc1ccccc1o2') from dual
*
ERROR at line 1:
ORA-29532: Java call terminated by uncaught Java exception: java.security.AccessControlException:
the Permission ("java.net.SocketPermission" "gareth-p51:1099" "connect,resolve") has not been
granted to -. The PL/SQL to grant this is dbms_java.grant_permission( '-',
'SYS:java.net.SocketPermission', 'gareth-p51:1099', 'connect,resolve' )


DEBUG com.cairn.rmi.client.TaskProxy - Connecting to rmi://gareth-p51:1099/TaskManager  [Root
Thread] (TaskProxy:89)
Exception in thread "Root Thread" java.security.AccessControlException: the Permission
("java.net.SocketPermission" "gareth-p51:1099" "connect,resolve") has not been granted to -. The
PL/SQL to grant this is dbms_java.grant_permission( '-', 'SYS:java.net.SocketPermission',
'gareth-p51:1099', 'connect,resolve' )
        at java.security.AccessControlContext.checkPermission(AccessControlContext.java)
        at java.security.AccessController.checkPermission(AccessController.java)
        at java.lang.SecurityManager.checkPermission(SecurityManager.java:551)
        at oracle.aurora.rdbms.SecurityManagerImpl.checkPermission(SecurityManagerImpl.java:210)
        at java.lang.SecurityManager.checkConnect(SecurityManager.java:1053)
        at sun.rmi.transport.tcp.TCPChannel.checkConnectPermission(TCPChannel.java:150)
        at sun.rmi.transport.tcp.TCPChannel.newConnection(TCPChannel.java:179)
        at sun.rmi.server.UnicastRef.invoke(UnicastRef.java:129)
        at
java.rmi.server.RemoteObjectInvocationHandler.invokeRemoteMethod(RemoteObjectInvocationHandler.java:
227)
        at java.rmi.server.RemoteObjectInvocationHandler.invoke(RemoteObjectInvocationHandler.java:179)
        at com.sun.proxy.$Proxy0.submitTask(Unknown Source)
        at com.cairn.rmi.client.TaskProxy.submit(TaskProxy:125)
        at com.cairn.rmi.oracle.Wrappers.molecularWeight(com.cairn.rmi.oracle.Wrappers:124)
c$cschem1@GJDEV>

```

For now return to building stubs using rmic and revisit issue when I 
have time.

The affected class is `com.cairn.rmi.server.TaskManagerImpl` and 
`com.cairn.rmi.server.TaskManagerImpl_Stub` needs to be included in
the OJVM wrapper jar.

### Loading RDKit library

Add the directory containing the Rdkit wrapper library to LD_LIBRARY_PATH
or java's java.library.path.

If the wrapper library is not found on the path the first file matching
 `*aphMolWrapper*` in the project's lib directory.
 
For the rdkit library to load cleanly (currently) RDBASE must be set.
I don't know if you really need to set to the rdkit install directory-
I have used the wrapper without the distribution successfully in the
past.


### Smarts can crash the server

This query `[#7;D3](*=*)(-&!@*)*:*` (from patty_rules.txt) run against 
en1000 will cause searches using trusted smiles to throw
GenericRDKitException. Now fixed by catching and repeating search using 
untrusted smiles. 

```
2019-05-10 18:36:12,430 INFO  com.cairn.rmi.index.TableIndex - Doing substructure search on CSCHEM1_TEST.EN1000.SMILES : [#7;D3](*=*)(-&!@*)*:* query length 22  [batchJobThread-0] (TableIndex.java:887)
2019-05-10 18:36:17,025 WARN  com.cairn.common.rdkit.SubstructureMatcher - GenericRDKitException matching target CCC1(c2ccc(Cl)cc2)NC(=O)N(CC(=O)OC)C1=O to query [#7&D3](-,:*=*)(-&!@*)*:*  [batchJobThread-0] (SubstructureMatcher.java:176)
2019-05-10 18:36:27,248 WARN  com.cairn.common.rdkit.SubstructureMatcher - GenericRDKitException matching target CCCCOC(=O)CN1C(=O)NC(CC)(c2ccc(Cl)cc2)C1=O to query [#7&D3](-,:*=*)(-&!@*)*:*  [batchJobThread-0] (SubstructureMatcher.java:176)
2019-05-10 18:36:39,912 WARN  com.cairn.common.rdkit.SubstructureMatcher - GenericRDKitException matching target CCC1(c2ccc(Cl)cc2)NC(=O)N(CC(=O)NCc2ccc(C)cc2)C1=O to query [#7&D3](-,:*=*)(-&!@*)*:*  [batchJobThread-0] (SubstructureMatcher.java:176)
2019-05-10 18:36:39,913 WARN  com.cairn.common.rdkit.SubstructureMatcher - GenericRDKitException matching target COC(=O)CN1C(=O)NC(C)(c2ccc(Cl)cc2Cl)C1=O to query [#7&D3](-,:*=*)(-&!@*)*:*  [batchJobThread-0] (SubstructureMatcher.java:176)
2019-05-10 18:36:39,914 WARN  com.cairn.common.rdkit.SubstructureMatcher - GenericRDKitException matching target COc1ccc2cc(C3(C)NC(=O)N(CC(=O)c4ccccc4)C3=O)ccc2c1 to query [#7&D3](-,:*=*)(-&!@*)*:*  [batchJobThread-0] (SubstructureMatcher.java:176)
2019-05-10 18:36:39,915 WARN  com.cairn.common.rdkit.SubstructureMatcher - GenericRDKitException matching target COc1ccc2cc(C3(C)NC(=O)N(CC(=O)N4CCCCC4)C3=O)ccc2c1 to query [#7&D3](-,:*=*)(-&!@*)*:*  [batchJobThread-0] (SubstructureMatcher.java:176)
2019-05-10 18:36:39,915 WARN  com.cairn.common.rdkit.SubstructureMatcher - GenericRDKitException matching target COc1ccc2cc(C3(C)NC(=O)N(CC(=O)NC4CCCC4)C3=O)ccc2c1 to query [#7&D3](-,:*=*)(-&!@*)*:*  [batchJobThread-0] (SubstructureMatcher.java:176)
2019-05-10 18:36:39,916 WARN  com.cairn.common.rdkit.SubstructureMatcher - GenericRDKitException matching target COc1ccc2cc(C3(C)NC(=O)N(CC(=O)NC4CC4)C3=O)ccc2c1 to query [#7&D3](-,:*=*)(-&!@*)*:*  [batchJobThread-0] (SubstructureMatcher.java:176)
2019-05-10 18:36:39,917 WARN  com.cairn.common.rdkit.SubstructureMatcher - GenericRDKitException matching target COc1ccc2cc(C3(C)NC(=O)N(CC(=O)C(C)(C)C)C3=O)ccc2c1 to query [#7&D3](-,:*=*)(-&!@*)*:*  [batchJobThread-0] (SubstructureMatcher.java:176)
2019-05-10 18:36:39,917 WARN  com.cairn.common.rdkit.SubstructureMatcher - GenericRDKitException matching target COC(=O)CN1C(=O)NC(C)(c2ccc3cc(OC)ccc3c2)C1=O to query [#7&D3](-,:*=*)(-&!@*)*:*  [batchJobThread-0] (SubstructureMatcher.java:176)
2019-05-10 18:36:39,918 WARN  com.cairn.common.rdkit.SubstructureMatcher - GenericRDKitException matching target Cc1cc(NC(=O)CN2C(=O)NC3(CCc4ccccc4C3)C2=O)no1 to query [#7&D3](-,:*=*)(-&!@*)*:*  [batchJobThread-0] (SubstructureMatcher.java:176)
2019-05-10 18:36:39,918 WARN  com.cairn.common.rdkit.SubstructureMatcher - GenericRDKitException matching target Cc1cc(Cl)ccc1NC(=O)CN1C(=O)NC2(CCc3ccccc3C2)C1=O to query [#7&D3](-,:*=*)(-&!@*)*:*  [batchJobThread-0] (SubstructureMatcher.java:176)
2019-05-10 18:36:39,919 WARN  com.cairn.common.rdkit.SubstructureMatcher - GenericRDKitException matching target O=C(CN1C(=O)NC2(CCc3ccccc3C2)C1=O)NCc1ccc(F)cc1 to query [#7&D3](-,:*=*)(-&!@*)*:*  [batchJobThread-0] (SubstructureMatcher.java:176)
2019-05-10 18:36:39,920 WARN  com.cairn.common.rdkit.SubstructureMatcher - GenericRDKitException matching target CCc1cccc(NC(=O)CN2C(=O)NC3(CCc4ccccc4C3)C2=O)c1 to query [#7&D3](-,:*=*)(-&!@*)*:*  [batchJobThread-0] (SubstructureMatcher.java:176)
```

### ORA-01460

Oracle error `ORA-01460: unimplemented or unreasonable conversion requested` will be thrown on Oracle 12 if a bound parameter is too large.
For example, of the contents of a mol2 file passed to the translate 
function may exceed this size.  The domain index operator bindings 
typically include clob parameters so as to avoid this issue.

The 'chem_structure.translateStructure' should be re-written to include
clob bindings.

As of Oracle 18 a bound string parameter can contain up to 32K characters.

### Permission errors after install

Connections that persist through an install fail with a Java permissions
error (an already granted network permission).  These can be resolved by closing the connection and reconnecting.

```
Java call terminated by uncaught Java exception: java.security.AccessControlException:
 the Permission (java.net.SocketPermission localhost resolve) has not been granted to C$CSCHEM1.
  The PL/SQL to grant this is 
  dbms_java.grant_permission( 'C$CSCHEM1', 'SYS:java.net.SocketPermission', 'localhost', 'resolve' ) ORA-06512
```