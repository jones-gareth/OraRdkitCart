.. _prerequisites:

Prerequisites
=============

Summary
*******

* Code base
* Oracle 12C or 18C
* Java 11 JDK
* RDKit Java library
* Maven 3.3.9 or later
* Correct network configuration

Code Base
*********

Pull the code base from github:

::

    git clone https://github.com/jones-gareth/OraRdkitCart.git

Oracle 12C or 18C
*****************

The cartridge has been tested on Oracle 12C and Oracle 18C. It would be expected to run on Oracle 19C,
but has not yet been tested.

Java 11 JDK
***********

The cartridge has been developed on OpenJDK 11.  It uses Java 11 syntax features and is not
compatible with earlier versions of Java.

RDKit Java library
******************

The cartridge loads the RDKit java JNI shared library and associated jar file.
The Java wrappers should be created by following the
`RDKit installation  <https://www.rdkit.org/docs/Install.html>`_ instructions.  Once the java wrappers have
been built the shared library *libGraphMolWrap.so* should be placed on *java.library.path* or in the lib
directory (of this project).  The cartridge will first try to load the wrapper library from the
path or, failing that, the project lib directory.

For convenience the project includes a pre-built wrapper library for Ubuntu 18.04 in the lib directory.
This library will not likely work on other Linux configurations (for example Centos/RHEL 7).

The pre-built library has the following direct dependencies that should be installed from OS repositories:

+----------------------------------+
| libboost_serialization.so.1.65.1 |
+----------------------------------+
| libpthread.so.0                  |
+----------------------------------+
| libboost_iostreams.so.1.65.1     |
+----------------------------------+
| libstdc++.so.6                   |
+----------------------------------+
| libm.so.6                        |
+----------------------------------+
| libgcc_s.so.1                    |
+----------------------------------+
| libc.so.6                        |
+----------------------------------+
| ld-linux-x86-64.so.2             |
+----------------------------------+

In addition the libz and bzip2 development libraries are required.

The project includes a local maven repository for the installation of libraries that are not available online.
The RDKit jar file should be installed in the project repository.
This project includes a pre-built jar file (*repo/org/rdkit/rdkit/1.0-SNAPSHOT/rdkit-1.0-SNAPSHOT.jar*)
which should be replaced if you have built your own wrapper.

::

    rm -r ~/.m2/repository/org/rdkit
    cp $RDBASE/build/Code/JavaWrappers/gmwrapper/libGraphMolWrap.so lib
    cp $RDBASE/Code/JavaWrappers/gmwrapper/org.RDKit.jar repo/org/rdkit/rdkit/1.0-SNAPSHOT/rdkit-1.0-SNAPSHOT.jar

Oracle JDBC driver
******************

The JDBC driver for Oracle is free to download but is proprietary and cannot be distributed with
the project.

Download the Oracle 18C JDBC drivers (ojdbc8.jar file only) from
`here <https://www.oracle.com/technetwork/database/application-development/jdbc/downloads/jdbc-ucp-183-5013470.html>`_
and save in the local repository (note the change in file name).

::

    mkdir -p repo/com/oracle/ojdbc/8/
    cp ojdbc8.jar repo/com/oracle/ojdbc/8/ojdbc-8.jar


Maven 3.3.9 or later
********************

The Java build reqires Maven 3.3.9 of later.  For ubuntu the  version in the OS repositories is fine, but
for RHEL/Centos 7 a newer version will need to be `downloaded <https://maven.apache.org/download.cgi>`_.

Correct network configuration
*****************************

For the case where the cartridge is installed on one machine and Oracle resides on another the
following ports should be open between the two hosts.

+-----------------------+------+-------------------------------------------+
| Oracle                | 1521 | May be changed in Oracle                  |
+-----------------------+------+-------------------------------------------+
| RMI server            | 1099 | Fixed port.                               |
+-----------------------+------+-------------------------------------------+
| Jar install webserver | 9100 |  Fixed port. Can be closed after install  |
+-----------------------+------+-------------------------------------------+

The 1098 port is used to create a webserver on the install machine that servers JAR files to the Oracle host.
These are loaded into the OJVM and the port may be closed after install.

RDBASE Environment Variable
***************************

The wrapper shared library will not load unless the environment variable RDBASE is set.  This should be set to
the directory containing the full RDKit distribution.  It would appear that cartridge functions only require that the
variable be set, not that it actually points to an RDKit distribution as the tests pass with *RDBASE=/tmp*.


* :ref:`Home <home>`


