
.. _implementation:

Implementation Notes
====================

Reactions
*********

The cartridge does not support reactions comprehensively.  However, reaction smiles in indexed columns
will be converted into a mixture of reactants and products.


Row level functions
*******************

The Oracle cartridge interface allows for domain index operators to do full index searches or
row level searches. The cartridge must implement both. In this cartridge, both
implementations do a full table search using the RMI server (even for the row level search a
few hundred RMI call-outs are going to take longer than a complete search).
When doing row level matches all matching rows are stored in a VARRAY which is searched
to see if the current row matches the query. Since the row level search is constantly looping
through the hit array it will always be more inefficient than the full index search (which returns
matching rows in bulk). It is not possible to use Oracle hashes/associative arrays for swift
retrieval as these are PL/SQL structures and cannot be stored in the index type (Oracle
objects can only have SQL members).

It appears that the optimizer always chooses a full index scan (though this could change in
the future). Dbms_output messages indicate which implementation is used when a domain
operator is used.

.. _transactions:

Transactions
************

Be wary of performing domain searching while editing the underlying table. Since the domain
index searches as another user in a separate connection it cannot be aware of uncommitted
edits. If you edit a row then do a domain index search prior to committing that row will be
searched using the wrong structure. If you delete a row, then do a search prior to committing,
and that row is returned as a hit you will get an Oracle internal error.

One possible fix is to only allow one session to edit a table at once (use “select for update .. “
to enforce). The index editing functions save this editing session id somewhere. All search
functions pass the editing transaction id to the RMI server. The RMI server can do a dirty
read of the change table if the current transaction is the editing transaction (SET
TRANSACTION ISOLATION LEVEL SERIALIZABLE or ALTER SESSION SET
ISOLATION_LEVEL SERIALIZABLE), or a normal read (SET TRANSACTION ISOLATION
LEVEL READ COMMITTED or ALTER SESSION SET ISOLATION_LEVEL READ
COMMITTED) otherwise. As another alternative to support transactions, the tables which
contain domain indexes could have triggers which call autonomous transactions (pragma
autonomous_transaction) which track transaction changes to the index.  Neither solutions
has been implemented yet.

Logging
*******

Logging is enabled on the RMI server using Apache log4j. The logger is configured in
bin/log4j.properties. The default configuration from the GUI installer creates both a
console appender and
a rolling file log in log/rmi/rmi_server.log.
The console appender is easily removed by This is easily changed by editing the log4j
configuration file.

There is another available appender which logs messages of level INFO or
higher to the Oracle table MESSAGE_TABLE (though connection errors obviously can't be
logged). It is disabled
by default and can be enabled by editing the setting database.use_database_appender in the
configuration file.

The Oracle java wrappers log to the console using log4j.  Messages may be found in the
Oracle trace file.  Alternatively the SQL command::

    CALL dbms_java.set_output(200000);

will direct Java output to SQL*Plus.

The PL/SQL functions and procedures print out various messages. To see them use::

    set serveroutput on size 1000000

Note that Java and PL/SQL messages are buffered within SQL*Plus and are not available
until an SQL command has completed.

RMI Networking
**************

The Oracle JVM contains the default rmi server hostname in the table c$cschem1.rmi_hostname.  It
may be changed via an SQL command::

    update rmi_hostname set rmi_hostname = 'rmi_server.company.com';

The rmi server contains settings for both the rmi server hostname (property *java.rmi.server.hostname*)
and the Oracle database host (property *credentials.host*).

In the case where the rmi server host or the Oracle host have multiple network interfaces the choice of
correct network names is particularly important.

PL/SQL Data Structures
**********************

This section summarizes the main PL/SQL packages and types used by the cartridge:

- CHEM_STRUCTURE this package provides utility functions and communicates with
  the RMI server though java functions and procedures.
- STRUCTURE_IND_OBJ this type implements the oracle extensible index interface.
  Includes all domain index searches.
- INDEX_UTIL this package assists STRUCTURE_IND_OBJ. Includes row-level
  searches and functional searches.


Trouble Shooting
****************

Besides the obvious scenario where the cartridge is not sufficiently resourced in terms of
memory this section documents other cases where the cartridge may break or appear to
break.

Cartridge is not responsive
---------------------------

Check all outputs for errors as described in Section 6.5 (page 23). In the default configuration
log4j messages will go to log/rmi/rmi_server.log. In a
Linux install standard error and output will end up in nohup.out in the bin directory. Note that
many cartridge functions are time consuming, though the first 100 rows matching any domain operator
will return immediately.
If there are no obvious error causes it may be worth restarting the cartridge.

Oracle internal errors
----------------------

If an index operation returns an internal error this may be due to an invalid row id. A commit
in the current session with
normally correct the error, see `transactions`_ above.
Otherwise, before contacting Oracle rebuild the index and verify
that the index is working correctly.

::

    select id
    from nci_open
    where c$cschem1.exactMatch(smiles, 'Cl', -1) = 1;
    2
    3 select id
    *
    ERROR at line 1:
    ORA-00600: internal error code, arguments: [12406], [], [], [], [],

Reinstalling and Uninstalling the Cartridge
*******************************************

You do not need to uninstall an existing cartridge prior to installing a new version of the
cartridge. Simply stop the RMI server and install any new version on top of the existing
cartridge. After a new install any structure indexes will need to be dropped and recreated.

To uninstall the cartridge first stop the RMI server. Next, connect to Oracle as sys and drop the
cartridge user and tester::

    SQL> drop user C$CSCHEM1 cascade;
    User dropped.
    SQL> drop user CSCHEM1_TEST cascade;
    User dropped.

Any structure indexes created by other users will still be present, though obviously it will no
longer be possible to perform chemical operations on them. While harmless, they can easily
be removed using the drop index command.
Finally, the cartridge distribution can be deleted.


* :ref:`Home <home>`
