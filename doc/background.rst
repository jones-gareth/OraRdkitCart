.. _background:

Background
==========

The cartridge provides domain indexes for tables that contain structures encoded in Daylight
smiles (or reactions encoded as smirks). This is achieved by using an external java RMI
service to perform substructure, similarity and exact match chemical searches. The RMI
service uses the RDKit toolkit to assist in smiles parsing and chemical structure handling.
The Oracle JVM is used to call out to the RMI service.

A domain index can be created be created for any user owner in a table table on a varchar2
column column containing structural smiles. This index can be uniquely named such that
index_name = owner.table.column. On index building the RMI server connects to oracle and
extracts smiles by Oracle ROWID. The external index is built as a hash keyed by ROWID
and with a data structure value which contains a fingerprint for the structure and the
canonical smiles. The index is then saved to a local cache file.

The index tracks edits to the base table using a change log (or journal table) that contains
ROWIDs and new values. Before performing any searches the domain index loads any new
entries from the change log into the index. An index rebuild saves these changes and
empties the log table.
Substructure search is achieved by fingerprinting a query SMARTs pattern (or MDL Mol
block) and screening out against fingerprints in the index. Those structures which
test against the fingerprint are searched exhaustively for a match. Similarity search is done
by fingerprinting a smiles query and determining Tanimoto coefficients for target structures.
The compounds that exceed a minimum similarity are returned. Exact match search is done
by canonicalizing the query smiles and doing string comparisons to determine matching
compounds.

The substructure search requires the creation of RDKit ROMols. These can be cached on
the RMI server in a LRU (least-recently used) cache. They are not currently cached in Oracle
and must be regenerated each time the RMI server starts. If enabled, the molecule cache can
simply retain OEMols that it generates for search or all molecules in an index can be added to
the cache. If you have sufficient memory to cache all the structures in your database and
plan to keep the RMI server running a long time a cache will improve performance.

RDKit pattern fingerprints are used in the data cartridge
for both screenout during substructure searches and in the calculation of
molecular similarity for similarity searches.

In order to improve performance during index build and substructure search thread pools can be assigned
to parallelize those operations.


* :ref:`Home <home>`
