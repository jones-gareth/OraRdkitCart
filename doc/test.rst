.. _test:

Testing
=======

The test suite runs test queries and operations against an installed data cartridge and
validates the results.

Once you have a running RMI server, run::

    mvn test

The tests will take a few minutes and the end of the output should look like::

    [INFO]
    [INFO] Results:
    [INFO]
    [WARNING] Tests run: 1173, Failures: 0, Errors: 0, Skipped: 1
    [INFO]
    [INFO] ------------------------------------------------------------------------
    [INFO] BUILD SUCCESS
    [INFO] ------------------------------------------------------------------------
    [INFO] Total time:  02:48 min
    [INFO] Finished at: 2019-06-01T17:46:34-06:00
    [INFO] ------------------------------------------------------------------------

There will be at least one expected error stack in the output.

* :ref:`Home <home>`

