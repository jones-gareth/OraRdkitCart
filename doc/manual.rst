.. _manual:

User Manual
===========

To illustrate the use of the domain index we will consider user *cschem1_test*, table
*nci_open* with column *smiles* containing structure. If you loaded the test data as
described :ref:`earlier <load_test>` you will be able to run the commands shown in this
section in SQL*Plus (while connected as cschem1_test).

Creating and Manipulating Domain Indexes
****************************************

To create an index *cschem1_test.molecules.smiles*::

    create index molecules_index on nci_open(smiles)indextype is
    c$cschem1.structureIndexType;

Creating the index takes about 1 minute (8 threads on an Intel quad core). To run all the commands in this
section you need to have created this index. You have the option to create this index on install.

To commit the change log table into the serialized Java object (emptying the change log table
for the index)::

    alter index molecules_index rebuild;

To completely rebuild the index (deleting the current change log table and serialized Java
object then rebuilding that object) use::

    alter index molecules_index rebuild parameters('full');

To unload/remove the index from the memory of the RMI server::

    alter index molecules_index parameters('unload');

To load the index into the RMI server memory::

    alter index molecules_index parameters('load');

To add all molecules in the index to the RMI server OEMol structure cache::

    alter index molecules_index parameters('add_to_cache');

This command will do nothing if the cache is not enabled.

The index will automatically adjust during column renames, table renames and table
truncates.

To delete the index use::

    drop index molecules_index;

Using Domain Index Operators
****************************

To perform substructure search use the *substructure(<column_name>, <query>, <max_hits>)*
operator, where *<query>* is a smarts pattern and *<max_hits>* is the maximum number of hits.
Set *<max_hits>* to -1 to retrieve all rows which match. For example::

    select id
      from nci_open
     where c$cschem1.substructure(smiles, '[#6]c2nc1ccccc1o2', -1) = 1;

The operator *mdl_substructure(<column_name>, <query>, <max_hits>)* is the same as the
substructure operator, but an MDL mol block is used as a query. The query can use '|' in
place of newlines::

    select id from nci_open
     where c$cschem1.mdl_substructure(SMILES, 'Cc2nc1ccccc1o2 |JME 2002.05 Tue Mar 17 15:39:01 PDT 2009|| 10 11                            V2000|    5.9792    1.4000    0.0000 C                               |    0.0000    2.1000    0.0000 C                               |    0.0000    0.7000    0.0000 C                               |    1.2124    2.8000    0.0000 C                               |    1.2124    0.0000    0.0000 C                               |    3.7564    2.5326    0.0000 N                               |    3.7564    0.2674    0.0000 O                               |    4.5792    1.4000    0.0000 C                               |    2.4249    2.1000    0.0000 C                               |    2.4249    0.7000    0.0000 C                               |  1  8  1  0         |  2  3  2  0         |  2  4  1  0         |  3  5  1  0         |  4  9  2  0         |  5 10  2  0         |  6  8  2  0         |  6  9  1  0         |  7  8  1  0         |  7 10  1  0         |  9 10  1  0         |M  END|', 10000) = 1;

The mdl_substructure operator will also bind a CLOB query, which
enables programs to pass MDL queries in excess of 4000 characters to the cartridge to Oracle 18C databases.

For exact match use the *exactMatch(<column_name>, <query>, <max_hits>)* where *<query>*
is a smiles structure. For example::

    select id
     from nci_open
    where c$cschem1.exactMatch(smiles, 'O=C1C(=CC(=O)C=C1)C', -1) = 1;

The similarity(<column_name>, <query>, <min_similarity>, <max_hits>) operator retrieves all
rows that have Tanimoto similarity of at least <min_similarity> to the query structure::

    select id from nci_open
     where c$cschem1.similarity(smiles, 'CCCc1ccc(cc1)S(=O)(=O)Nc2cc(on2)C', 0.80, -1) = 1;

An ancillary operator smililarityScore is provided to retrieve similarity scores::

    select c$cschem1.similarityScore(1), id
      from nci_open
     where c$cschem1.similarity(smiles, 'CCCc1ccc(cc1)S(=O)(=O)Nc2cc(on2)C', 0.80, -1, 1) = 1;

The integer argument in the ancillary operator must match the extra integer argument in the
similarity function (this is an Oracle requirement).

When not to use the Domain Index Operators
******************************************

The domain index always does a full table scan and returns all hits. If you have a complex
query with a simple substructure query then the full table scan will be inefficient. Consider
this::

    select count(id)
      from nci_open
     where c$cschem1.substructure(smiles, 'c1ccccc1', -1) = 1
       and id between 170000 and 171000;

The domain index search returns almost 200000 hits and these are filtered by the id clause to
give about 600 hits. It would be more efficient to get approximately 1000 hits from the id
clause first and then perform substructure search on those. Unfortunately, the Oracle
cartridge implementation allows only a full table scan or a single row test. Each single row
test would require a call to the RMI server and is not practical for 1000 rows.
To get better performance you can pass SQL that returns ROWIDS to the domain index (see
CHEM_STRUCTURE documentation for :ref:`tableIndexSubstructSqlFilter <tableIndexSubstructSqlFilter>`)::

    select count(id) from nci_open m
     where rowid in (select * from
     table(c$cschem1.chem_structure.tableIndexSubstructSqlFilter ('cschem1_test', 'nci_open', 'smiles',
                        'select rowid from cschem1_test.nci_open where id between 170000 and 171000',
                        'c1ccccc1', -1)));

There is an analogous function *chem_structure.tableIndexSimilasritySqlFilter* for similarity
searches.

Functional Operators
********************

The domain index operators substructure, exactMatch and similarity are not available as
functional operators that can be applied to any text column. While this is easily implemented,
a design decision was taken to not provide this functionality. Because each row tested using
a functional operator will necessitate a call-out to the RMI server, a search on an un-indexed
table is likely to overwhelm the RMI server.

CHEM_STRUCTURE Functions and Procedures
***************************************

Utility functions
-----------------

These are general structure handling functions that do not require domain indexes. In
general each function call requires an RMI call-out. Single functions such as
molecularWeight or translateStructure should be avoided in select statements that process
many rows. If there is interest the single value function can be converted to a domain index
operator or a batch function.

*MolecularWeight(<smiles>)* takes a smiles argument and returns the molecular weight::

    select c$cschem1.chem_structure.molecularWeight('c1ccccc1') from dual;

*TranslateStructure(<smiles>, <from>, <to>)* translates between structure formats.

+----------------------+-------+-----+
| Format               | From  | To  |
+----------------------+-------+-----+
| smarts               |  Y    |  Y  |
+----------------------+-------+-----+
| smiles/can_smiles    |  Y    |  Y  |
+----------------------+-------+-----+
| mdl/mol              |  Y    |  Y  |
+----------------------+-------+-----+
| mol2                 |  Y    |  N  |
+----------------------+-------+-----+
| pdb                  |  Y    |  Y  |
+----------------------+-------+-----+
| molecular_formula    |  N    |  N  |
+----------------------+-------+-----+

The structure formats are listed above.  Smiles are always canonicalized on output.

::

    select c$cschem1.chem_structure.translateStructure('CCO', 'smiles', 'pdb') from dual;

The translateStructure function may be hampered on Oracle 12 due to the 4000 character limit for
VARCHAR2 strings in PL/SQL function parameters.  If that length is exceeded the Oracle
error *ORA-01460: unimplemented or unreasonable conversion requested*
will occur.

*CanonicalizeSmiles(<smiles>)* takes a smiles and canonicalizes it. It will return a smiles even
if normalization fails::

    select c$cschem1.chem_structure.canonicalizeSmiles('Cc2nc1ccccc1o2') from dual;

Index Procedures
----------------

.. _tableIndexSubstructSqlFilter:

*TableIndexSubstructSqlFilter(<owner>, <table>, <column>, <sql_filter>, <query>,
[<max_hits>], [<query_type>])* returns a PL/SQL table of ROWIDS for the index
owner.table.column for structures which match both the smarts substructure query and the
sql_filter. The sql_fiter query should return a single column of ROWIDS, which are then
tested against the substructure query. Set query_type = 'mdl' to use an MDL mol block as a
query (you can use | for newline in the query). Note that the SQL in sql_filter will be run by the
c$cschem1 user (using the permissions of the current user) so full schema names of tables are required.

::

    select id from nci_open m
     where rowid in
           (select * from table(
                  c$cschem1.chem_structure.tableIndexSubstructSqlFilter
                     ('cschem1_test', 'NCI_OPEN', 'SMILES',
                      'select rowid from cschem1_test.nci_open where rownum < 30000',
                      '[#6]c2nc1ccccc1o2', -1)));

*TableIndexSimilaritySqlFilter(<owner>, <table>, <column>, <sql_filter>, <query>, <max_hits>)*
returns a PL/SQL table of ROWIDS and smilarity scores for the index owner.table.column for
structures which both pass the sql_filter and have at least min_similarity similarity with the
query structure. The sql_fiter query should return a single column of ROWIDS, which are
then tested against the similarity query. Note that the SQL in sql_filter will be run by the
c$cschem1 user so full schema names of tables are required.

::

    select id, score
      from table(
              c$cschem1.chem_structure.tableIndexSimilaritySqlFilter
                  ('cschem1_test', 'NCI_OPEN', 'SMILES',
                   'select rowid from cschem1_test.nci_open where rownum < 30000',
                   'CCCc1ccc(cc1)S(=O)(=O)Nc2cc(on2)C', 0.7, -1)) s,
           nci_open m
     where m.rowid = s.hit_rowid;

These functions and procedures extract information from the external index.

*TableIndexExtractSmiles (<owner_name>, <table_name>, <column_name>, <sql_query>,
<sql_update>, <sql_user>, <sql_password>)* can extract the canonical smiles and pattern fingerprints
(as  Base64 strings) from the index and return them to Oracle. The SQL in sql_query should
return a rowid from a
domain index in column 1 and any unique column corresponding to that rowid in column 2.
For example::

    select rowid, id from nci_open

The SQL in update_sql should be an insert or update query which takes 3 bind parameters:
smiles (varchar2(1000)), fingerprint (varchar2(4000)) and the unique id from sql_query in that
order. For example, to store index smiles and fingerprints in the nci_open table you will first
need to create additional columns::

    alter table nci_open add(canonical_smiles varchar2(1000), string_fingerprint varchar2(4000));

Then you can use this update statement for update_sql::

    update nci_open set canonical_smiles = :1, string_fingerprint = :2 where id = :3

Because this procedure updates a user's schema, user credentials are also required
(substitute 'secret' for your password). Run the command like this::

    begin
      c$cschem1.chem_structure.tableIndexExtractSmiles (
        'CSCHEM1_TEST', 'NCI_OPEN', 'SMILES',
        'select rowid, id from nci_open',
        'update nci_open set canonical_smiles = :1, string_fingerprint = :2 where id = :3',
        'cschem1_test', 'secret');
    end;
    /

This command is time-consuming- it may take 30s per 100000 structures.

The function *tableIndexGetRowSmiles(<owner>, <table>, <column>, <row_id>)* returns the
smiles held in the external index for a given row. The related function
*tableIndexGetIdSmiles(<owner>, <table>, <column>, <id_column>, <id_value>)* allows you to
specify a primary key (or unique) column and value and return the external smiles for the
matching row.

::

    select c$cschem1.chem_structure.tableIndexGetIdSmiles('cschem1_test', 'NCI_OPEN', 'SMILES', 'ID', '123456')
      from dual;

The function *tableIndexGetRowFingerprint(<owner>, <table>, <column>, <row_id>,
<hex_format>)* returns the fingerprint held in the external index for a given row. The related
function *tableIndexGetIdFingerprint(<owner>, <table>, <column>, <id_column>, <id_value>,
[<hex_format>])* allows you to specify a primary key (or unique) column and value and return
the external fingerprint for the matching row::

    select c$cschem1.chem_structure.tableIndexGetIdFingerprint('cschem1_test', 'NCI_OPEN', 'SMILES', 'ID', '123455')
      from dual;

The function *tableIndexGetRowSimilarity(<owner>, <table>, <column>, <row_id1>,
<row_id2>)* returns the pairwise Tanimoto similarity for two rows. The related function
*tableIndexGetIdSimilarity(<owner>, <table>, <column>, <id_column>, <id_value1>,
<id_value2>)* allows you to specify a primary key (or unique) column and two values and
return the Tanimoto similarity score for the two associated rows.

De Morgan/Extended Fingerprints
*******************************

The cartridge also supports the addition of Morgan/Circular fingerprints.  These may be added as
an option during index creation and operators are provided to perform similarity search using those
fingerprints

The fingerprint type is specified by adding a fp=<type> parameter to the create index command.  Multiple
fingerprints may be added to a single index.  The following fingerprint types are supported (see
`the RDKit documentation <https://www.rdkit.org/docs/GettingStartedInPython.html#morgan-fingerprints-circular-fingerprints>`_
for an explanation of the *features* and *radius* terms).

+------------------+----------+--------------+
| Fingerprint type | Features | RDKit Radius |
+------------------+----------+--------------+
| ECFP4            | N        | 2            |
+------------------+----------+--------------+
| ECFP6            | N        | 3            |
+------------------+----------+--------------+
| FCFP4            | Y        | 2            |
+------------------+----------+--------------+
| FCFP6            | Y        | 3            |
+------------------+----------+--------------+

To build an index with ECFP4 and FCFP6 fingerprints use::

    drop index molecules_index;
    create index molecules_index on nci_open(smiles) indextype is c$cschem1.structureIndexType parameters('fp=ecfp4 fp=fcfp6');

Once built, the similarity searches may be run on the index using the operator
*extended_similarity(<column>, <fingerprint type>, <similarity_method>, <query_smiles>, <min_similarity>, <alpha>, <beta>)*.
Where *similarity_method* is one of *tanimoto*, *dice* or *tversky*, *Alpha* and *beta* are the weighting
parameters of the tversky method and may be set to NULL for tanimoto or dice methods.

::

    select id, smiles from nci_open
     where c$cschem1.extended_similarity(smiles, 'ecfp4', 'tanimoto', 'C(C)NS(=O)(=O)NCC', 0.7, -1, NULL, NULL) = 1;

    select id, smiles from nci_open
     where c$cschem1.extended_similarity(smiles, 'fcfp6', 'tversky', 'C(C)NS(=O)(=O)NCC', 0.7, -1, 0.2, 0.8) = 1;

As with the original similarity operator a similarity score ancillary operator is available to retrieve the similarity
scores.  Note the extra integer argument in the extended_similarity operator which must match the argument to the
ancillary operator.

::

    select c$cschem1.similarityScore(1), id, smiles from nci_open
     where c$cschem1.extended_similarity(smiles, 'ecfp4', 'tanimoto', 'C(C)NS(=O)(=O)NCC', 0.7, -1, NULL, NULL, 1) = 1;

    select c$cschem1.similarityScore(1), id, smiles from nci_open
     where c$cschem1.extended_similarity(smiles, 'fcfp6', 'tversky', 'C(C)NS(=O)(=O)NCC', 0.7, -1, 0.2, 0.8, 1) = 1;


CLOB structure columns
*******************************

In addition to building indices on VARCHAR2 columns containing smiles strings, you can also construct indices on
CLOB columns containing MLD mol blocks.  Inserting mol blocks into table rows containing CLOBs typically entails
population the table through an Oracle API.  The test class *com.cairn.rmi.test.client.TestClobColumn* contains
code which adds MOL blocks to a CLOB column using Java and JDBC drivers.

* :ref:`Home <home>`
