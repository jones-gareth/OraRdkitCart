.. _build:

Building the code
=================

To build the codebase use maven

::

    mvn -DskipTests clean install

The *skipTests* definition is required as the test suite is client based and requires an
installed cartridge to test against.

The build command will create an assembly jar in the target directory.




* :ref:`Home <home>`

