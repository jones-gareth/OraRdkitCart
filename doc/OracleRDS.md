
### Cartridge for Oracle on Amazon RDS

#### Creating RDS Instance

See background to [OJVM on RDS](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/oracle-options-java.html)
and [RDS option groups] (https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_WorkingWithOptionGroups.html)

* Create option group for Oracle SE2 Major engine version 12.2
* Add JVM option to option group
* Must enable minor version upgrade
* Oracle Java isn't supported for the db.m1.small, db.t2.micro, or db.t2.small DB 
* For the RMI server create an Ubuntu Bionic host in the same 
  security group as the RDS database.
* Install all the RMI stuff on the Ubuntu host
* export RDBASE=/tmp
* Install Oracle instant client base and SQL*Plus on RMI server
* Open ports 22, 1099, 9100 and 1521 in security group

#### Code base preparation

* Edit `sql/permissions.sql`
    * comment out `call dbms_java.grant_permission( 'C$CSCHEM1', 'SYS:java.security.SecurityPermission', 'createAccessControlContext', '' );`
    * uncomment `-- grant  RDS_JAVA_ADMIN to c$cschem1;`
* Edit com.cairn.rmi.installer.InstallerModel#getAdminConnection
  replacing `sys` by the the RDS admin user.
* Edit com.cairn.rmi.installer.InstallerModel#loadJavaJarfile so
  that on failure it will commit and return true.
* Optionally edit com.cairn.rmi.installer.InstallerModel#loadJarFiles,
  commenting out `server.stop()`.  This allows you to load jar files
  by hand in SQL*Plus as long as the installer is running.
  
#### JDBC connection options step

* Use end point from connectivity tab on RDS database page as hostname

#### Install Cartrige Step

* For the RMI Hostname use the private IP address of the EC2 instance 
  hosting the RMI server.  The OJVM is not able to use a name.
  
#### Fix JAR Errors during install

The JAR files will all fail to install with this error:

```
java.sql.SQLException: ORA-29532: Java call terminated by uncaught 
Java exception: java.security.AccessControlException: the Permission
 ("java.lang.RuntimePermission" "getenv.TNS_ADMIN") has not been granted
  to C$CSCHEM1. The PL/SQL to grant this is 
  dbms_java.grant_permission( 'C$CSCHEM1', 'SYS:java.lang
  .RuntimePermission', 'getenv.TNS_ADMIN', '' )
```

However, this error is thrown after the class files have been loaded and 
prior to building.  So all classes are loaded, but they are invalid.

Leaving the install at the *Grant Permissions* step, connect to the 
database as c$cschem1:

```
rlwrap ./sqlplus 'c$cschem1@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=gjdev.c8aaukv4wmiw.us-east-1.rds.amazonaws.com)(PORT=1521))(CONNECT_DATA=(SID=GJDEV)))'
```

Repeatedly (5 or 6 times) run this
[SQL script](http://11iappsdba.blogspot.com/2009/12/compile-invalid-java-objects.html) 
until all Java classes compile:

```
set echo off
set feedback off
set pagesize 0
set linesize 1000

spool alter_java.sql

select 'ALTER JAVA SOURCE "' || object_name || '" COMPILE;'
from user_objects
where object_type = 'JAVA SOURCE'
and status = 'INVALID'
/

select 'ALTER JAVA CLASS "' || object_name || '" RESOLVE;'
from user_objects
where object_type = 'JAVA CLASS'
and status = 'INVALID'
/

spool off
set feedback on
set pagesize 120
set echo on
@alter_java.sql

/

```

Return to the *grant permissions" step and finish the install.


#### Useful stuff

To see anything useful in SQL*Plus from java calls:

```
set serveroutput on size 1000000
CALL dbms_java.set_output(200000);
```

To see status of Java classes
```
SELECT dbms_java.longname (object_name), status  FROM user_objects where object_type = 'JAVA CLASS';
```

To load classes from running webserver use something like:

```
call dbms_java.loadjava('-f -v http://172.31.50.7:9100/log4j-1.2.17.jar');
call dbms_java.loadjava('-f -v http://172.31.50.7:9100/commons-lang-2.6.jar');
call dbms_java.loadjava('-f -v -r http://172.31.50.7:9100/oracle-wrapper-sources.jar');
```

To list all enabled Java security permissions (as admin user):

```
SELECT * FROM dba_java_policy 
   WHERE grantee IN ('RDS_JAVA_ADMIN', 'PUBLIC') 
   AND enabled = 'ENABLED' 
   ORDER BY type_name, name, grantee;        
```

