
-- drop user C$CSCHEM1 cascade;
create user C$CSCHEM1 identified by "&varPasswd" default tablespace &varUserTS temporary tablespace &varTempTS;
grant connect, resource to C$CSCHEM1;
grant select any table to C$CSCHEM1;
grant unlimited tablespace to C$CSCHEM1;

--  do this as sys:

-- grant execute on dbms_java to C$CSCHEM1;
grant create any table to  C$CSCHEM1;

call dbms_java.grant_permission( 'C$CSCHEM1', 'SYS:java.net.SocketPermission', '*', 'connect,resolve' );
call dbms_java.grant_permission( 'C$CSCHEM1', 'java.net.SocketPermission', '*', 'connect,resolve' );


-- Originally needed to open log4j without error- doesn't seem to be required anymore
-- call dbms_java.grant_permission( 'C$CSCHEM1', 'SYS:java.util.logging.LoggingPermission', 'control', '' );

-- comment out for AWS RDS
call dbms_java.grant_permission( 'C$CSCHEM1', 'SYS:java.lang.RuntimePermission', 'getClassLoader', '' );

-- required by Oracle 12 but not 18
call dbms_java.grant_permission( 'C$CSCHEM1', 'SYS:java.security.SecurityPermission', 'createAccessControlContext', '' );

-- use this for Oracle RDS
-- grant  RDS_JAVA_ADMIN to c$cschem1;


-- create cartridge tester
-- drop user CSCHEM1_TEST cascade;
create user CSCHEM1_TEST identified by "&varPasswd" default tablespace &varUserTS temporary tablespace &varTempTS;
grant connect, resource to CSCHEM1_TEST;
grant unlimited tablespace to  CSCHEM1_TEST;




