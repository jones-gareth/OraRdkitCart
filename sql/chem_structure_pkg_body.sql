create or replace package body chem_structure as

/*
   Functions for chemical structure handling.

   Contains wrapper for java functions which use RMI to access remote procedures
   that use JOEChem to manipulate and search compounds.

   Package contains wrappers for java functions and helper routines.

   Gareth Jones 1/08
*/

function molecularWeight(
	smiles varchar2)
return number
as
begin
  return molecularWeight(null, smiles);
end;

/*
    Wrapper for Java RMI function to determine molecular weight
 */
function molecularWeight(
         rmi_hostname varchar2,
	smiles varchar2)
return number
as
  LANGUAGE JAVA NAME
	'com.cairn.rmi.oracle.Wrappers.molecularWeight
		(java.lang.String, java.lang.String)
	 return float';

function exactMass(
	smiles varchar2)
return number
as
begin
  return exactMass(null, smiles);
end;

/*
    Wrapper for Java RMI function to determine molecular weight
 */
function exactMass(
         rmi_hostname varchar2,
	smiles varchar2)
return number
as
  LANGUAGE JAVA NAME
	'com.cairn.rmi.oracle.Wrappers.exactMass
		(java.lang.String, java.lang.String)
	 return float';

/*
    Wrapper for Java RMI function to translate from one structural
    format to another.
 */
function translateStructure(
	inputMolecule varchar2,
	fromFormat varchar2,
  toFormat varchar2)
return varchar2
as
  LANGUAGE JAVA NAME
	'com.cairn.rmi.oracle.Wrappers.translate
		(java.lang.String, java.lang.String, java.lang.String)
	 return java.lang.String';

/*
    Wrapper for Java RMI function to generate a fingerprint from a smiles
    string.  Set query to true if the structure is a smarts.
*/
function getFingerprint(
	query boolean,
	inputMolecule varchar2)
return varchar2
as
  LANGUAGE JAVA NAME
	'com.cairn.rmi.oracle.Wrappers.getFingerprint
		(boolean, java.lang.String)
	 return java.lang.String';

/*
    Like getFingerprint, but you can pass 'Y' or 'N' instead of a boolean for
    specifing a query smarts, so that this can be used in sql.
*/
function sqlGetFingerprint(
	strQuery char,
	inputMolecule varchar2)
return varchar2
as
    query boolean;
begin
    if strQuery = 'Y'
    then
        query := true;
    else
        query := false;
    end if;

    return getFingerprint(query, inputMolecule);
end;

/*
    Wrapper for Java RMI function to generate pair-wise Tanimoto from two
    smiles strings strings
*/
function tanimotoSimilarity(
	structure1 varchar2,
    structure2 varchar2)
return number
as
  LANGUAGE JAVA NAME
	'com.cairn.rmi.oracle.Wrappers.tanimotoSimilarity
		(java.lang.String, java.lang.String)
	 return double';

function canonicalizeSmiles (
	smiles varchar2)
return varchar2
as
begin
    return canonicalizeSmiles(null, smiles);
end;

/*
    Wrapper for Java RMI function to canonicalize smiles
 */
function canonicalizeSmiles (
        rmi_hostname varchar2,
	smiles varchar2)
return varchar2
as
  LANGUAGE JAVA NAME
	'com.cairn.rmi.oracle.Wrappers.canonicalizeSmiles
		(java.lang.String, java.lang.String)
	 return java.lang.String';

/*
    Wrapper for java function that adds an array of smiles to the rmi
    structure cache
*/
procedure addStructuresToCache(
        input_smiles in stringArray)
as
  LANGUAGE JAVA NAME
	'com.cairn.rmi.oracle.Wrappers.addStructuresToCache
        (java.sql.Array)';

procedure enableCache
as
  LANGUAGE JAVA NAME
	'com.cairn.rmi.oracle.Wrappers.enableCache ()';
    
procedure disableCache
as
  LANGUAGE JAVA NAME
	'com.cairn.rmi.oracle.Wrappers.disableCache ()';

/*
    Takes a SQL query that returns smiles and adds those smiles to the rmi
    structure cache.
*/
procedure sqlQueryStructuresToCache (
        smiles_query in varchar2)
as
	structure_csr structure_cursor;
begin
        open structure_csr for smiles_query;
        queryStructuresToCache(structure_csr);
        --close structure_csr;
end;

/*
    Takes a cursor that returns smiles and adds those smiles to the rmi
    structure cache.
*/
procedure queryStructuresToCache (
	structure_csr in structure_cursor)
as
        smiles stringArray;
        total integer;
begin
        total := 0;
        smiles := stringArray();

        -- add smiles to cache in batch mode
        loop

            -- first fetch a batch of ids and smiles into array
            fetch structure_csr bulk collect into smiles limit smiles.limit;

            if smiles.count > 0
            then
                -- now add smles to cache
                dbms_output.put_line('Adding '||smiles.count||' smiles to cache');
                addStructuresToCache(smiles);
                total := total + smiles.count;
            end if ;

            exit when structure_csr%notfound;
        end loop;

        dbms_output.put_line('Added total '||total||' smiles to cache');
        close structure_csr;
        return;
end;



/*
    Wrapper for functional RMI Substructure search.
*/
function funcSubstructureSearch (
        target varchar2,
        query varchar2,
        query_type varchar2)
return boolean
as language Java name
'com.cairn.rmi.oracle.Wrappers.functionalSubstructureSearch(java.lang.String,
			java.lang.String, java.lang.String)
      return boolean';

/*
    Wrapper for functional RMI Substructure search with clob query.
*/
function funcClobSubstructureSearch (
        target varchar2,
        query clob,
        query_type varchar2)
return boolean
as language Java name
'com.cairn.rmi.oracle.Wrappers.functionalClobSubstructureSearch(java.lang.String,
       oracle.sql.CLOB, java.lang.String)
      return boolean';


/*
    Performs substructure search on a table indexed in an external RMI process.
    Query is a smarts pattern and matching rowids are retrurned
*/
function tableIndexSubstructureSearch (
        owner_name varchar2,
        table_name varchar2,
        column_name varchar2,
        query varchar2,
        max_hits integer := -1,
        use_fingerprint char := 'Y',
        query_type varchar2 := 'smarts')
return hit_table
as
    hit_list rowIdArray;
    cnt integer;
    v_hit_table hit_table;
    v_use_fingerprint boolean := true;
    finished boolean;
    job_no integer;
    rmi_hostname varchar2(1000);
    structure_ind structure_ind_obj;
begin

    hit_list := rowIdArray();
    v_hit_table := hit_table();
    if use_fingerprint = 'N' then
        v_use_fingerprint := false;
    end if;

    rmi_hostname := index_common.get_rmi_hostname
        (structure_ind_obj.index_type_name, owner_name, table_name, column_name);

    index_utl.indexSubstructureSearch(rmi_hostname, finished, false, job_no,
                            owner_name, table_name, column_name, query,
                            hit_list, max_hits, v_use_fingerprint, query_type);

    structure_ind := structure_ind_obj(null, index_common.boolean_to_integer(finished), job_no, hit_list);
    structure_ind.build_hitlist_table := 1;
    structure_ind.rmi_hostname := rmi_hostname;
    structure_ind.append_scores_to_table;
    structure_ind.fetch_all_chunks;

    return structure_ind.hitlist_table;
end;

/*
    Wrapper for functional RMI exact match search.
*/
function funcExactMatchSearch (
        target varchar2,
        query varchar2)
return boolean
as language Java name
'com.cairn.rmi.oracle.Wrappers.functionalExactMatchSearch(java.lang.String,
      java.lang.String)
      return boolean';

/*
    Performs exact match search on a table indexed in an external RMI process.
    Query is a smiles structure and matching rowids are retrurned
*/
function tableIndexExactMatchSearch (
        owner_name varchar2,
        table_name varchar2,
        column_name varchar2,
        query varchar2,
        max_hits integer := -1)
return hit_table
as
    hit_list rowIdArray;
    finished boolean;
    job_no integer;
    structure_ind structure_ind_obj;
    rmi_hostname varchar2(1000);
begin

    hit_list := rowIdArray();
    rmi_hostname := index_common.get_rmi_hostname
        (structure_ind_obj.index_type_name, owner_name, table_name, column_name);
    index_utl.indexExactMatchSearch(rmi_hostname, finished, false, job_no, owner_name, table_name,
                          column_name, query, hit_list, max_hits);
    structure_ind := structure_ind_obj(null, index_common.boolean_to_integer(finished),
                                       job_no, hit_list);
    structure_ind.rmi_hostname := rmi_hostname;
    structure_ind.build_hitlist_table := 1;
    structure_ind.append_scores_to_table;
    structure_ind.fetch_all_chunks;

    return structure_ind.hitlist_table;
end;



/*
    Wrapper for functional RMI similarity search
*/
function funcSimilaritySearch (
        target varchar2,
        query varchar2,
        minSimilarity number)
return boolean
as language Java name
'com.cairn.rmi.oracle.Wrappers.functionalSimilaritySearch(java.lang.String,
      java.lang.String, double)
      return boolean';

/*
    Performs substructure search on a table indexed in an external RMI process.
    Query is a smiles structure and matching rowids are returned
*/
function tableIndexSimilaritySearch (
        owner_name varchar2,
        table_name varchar2,
        column_name varchar2,
        query varchar2,
        min_similarity number,
        max_hits integer := -1)
return similarity_hits
as
    hit_list rowIdArray;
    similarity_list doubleArray;
    cnt integer;
    finished boolean;
    job_no integer;
    structure_ind structure_ind_obj;
    rmi_hostname varchar2(1000);
begin

    hit_list := rowIdArray();
    similarity_list := doubleArray();
    rmi_hostname := index_common.get_rmi_hostname
        (structure_ind_obj.index_type_name, owner_name, table_name, column_name);

    index_utl.indexSimilaritySearch(rmi_hostname, finished, false, job_no,
                          owner_name, table_name, column_name, query,
                          min_similarity, hit_list, similarity_list, max_hits);

    structure_ind := structure_ind_obj(null, index_common.boolean_to_integer(finished),
                                       job_no, hit_list, similarity_list);
    structure_ind.rmi_hostname := rmi_hostname;
    structure_ind.build_score_table := 1;
    structure_ind.append_scores_to_table;
    structure_ind.fetch_all_chunks;

    return structure_ind.score_table;
end;


/*
    Like tableIndexOperation, but you can use it in SQL
*/
function sqlTableIndexOperation (
        owner_name varchar2,
        table_name varchar2,
        column_name varchar2,
        operation varchar2)
return char
as
    test boolean;
    rmi_hostname varchar2(1000);
begin

    rmi_hostname := index_common.get_rmi_hostname
        (structure_ind_obj.index_type_name, owner_name, table_name, column_name);


    test := index_utl.tableIndexOperation(rmi_hostname, owner_name, table_name, column_name, operation);
    if test
    then
        return 'Y';
    else
        return 'N';
    end if;
end;

/*
    Wrapper for Substructure search on RMI indexes,
    with initial SQL search to select rows
*/
procedure indexSubstructureSqlFilter (
        rmi_hostname varchar2,
        finished out boolean,
        add_to_map boolean,
        job_no out number,
        owner_name varchar2,
        table_name varchar2,
        column_name varchar2,
        sql_filter varchar2,
        query varchar2,
        hits out nocopy rowIdArray,
        max_hits number,
        bind_params paramArray,
        query_type varchar2)
as language Java name
'com.cairn.rmi.oracle.Wrappers.tableIndexSubstructureSearchSqlFilter
                       (java.lang.String, boolean[], boolean, int[],
                        java.lang.String, java.lang.String,
                        java.lang.String, java.lang.String, java.lang.String,
                        java.sql.Array[], int, java.sql.Array, java.lang.String)';


/*
    Performs substructure search on a table indexed in an external RMI process.
    Query is a smarts pattern and matching rowids are retrurned.
    Sql Filter is an initial sql query to select rows for substructure search.
*/
function tableIndexSubstructSqlFilter (
        owner_name varchar2,
        table_name varchar2,
        column_name varchar2,
        sql_filter varchar2,
        query varchar2,
        max_hits integer := -1,
        bind_params paramArray := paramArray(),
        query_type varchar2 := 'smarts')
return hit_table
as
    hit_list rowIdArray;
    cnt integer;
    v_hit_table hit_table;
    finished boolean;
    job_no integer;
    structure_obj structure_ind_obj;
    rmi_hostname varchar2(1000);
begin

    hit_list := rowIdArray();
    v_hit_table := hit_table();
    rmi_hostname := index_common.get_rmi_hostname
        (structure_ind_obj.index_type_name, owner_name, table_name, column_name);


    indexSubstructureSqlFilter(rmi_hostname, finished, false, job_no, owner_name, table_name, column_name, sql_filter,
                               query, hit_list, max_hits, bind_params, query_type);

    -- save hitlist in scan context
    structure_obj := structure_ind_obj(null, index_common.boolean_to_integer(finished),
                                 job_no, hit_list);

    -- get all remaining chunks
    structure_obj.rmi_hostname := rmi_hostname;
    structure_obj.build_hitlist_table := 1;
    structure_obj.append_scores_to_table;
    structure_obj.fetch_all_chunks();

    return structure_obj.hitlist_table;
end;

/*
    Wrapper for Java proceedure that performs similarity search,
    on rows that are seleceted from an sql filter.
*/
procedure indexSimilaritySqlFilter (
        rmi_hostname varchar2,
        finished out boolean,
        add_to_map boolean,
        job_no out number,
        owner_name varchar2,
        table_name varchar2,
        column_name varchar2,
        sql_filter varchar2,
        query varchar2,
        min_similarity number,
        hits out nocopy rowIdArray,
        similarity out nocopy doubleArray,
        max_hits number,
        bind_params paramArray)
as language Java name
'com.cairn.rmi.oracle.Wrappers.tableIndexSimilaritySearchSqlFilter
                       (java.lang.String, boolean[], boolean, int[],
                        java.lang.String, java.lang.String, java.lang.String,
                        java.lang.String, java.lang.String,
                        double, java.sql.Array[], java.sql.Array[], int,
                        java.sql.Array)';


/*
    Performs substructure search on a table indexed in an external RMI process.
    Query is a smiles structure and matching rowids are retrurned.
    Matching rows must first be selected by an SQL query filter
*/
function tableIndexSimilaritySqlFilter (
        owner_name varchar2,
        table_name varchar2,
        column_name varchar2,
        sql_filter varchar2,
        query varchar2,
        min_similarity number,
        max_hits integer := -1,
        bind_params paramArray := paramArray())
return similarity_hits
as
    hit_list rowIdArray;
    similarity_list doubleArray;
    cnt integer;
    v_similarity_table similarity_hits;
    finished boolean;
    job_no integer;
    structure_obj structure_ind_obj;
    rmi_hostname varchar2(1000);
begin

    --hit_list := rowIdArray();
    --similarity_list := doubleArray();
    v_similarity_table := similarity_hits();
    rmi_hostname := index_common.get_rmi_hostname
        (structure_ind_obj.index_type_name, owner_name, table_name, column_name);

    indexSimilaritySqlFilter(rmi_hostname, finished, false, job_no, owner_name, table_name, column_name, sql_filter, query,
                            min_similarity, hit_list, similarity_list, max_hits, bind_params);

     -- save hitlist in scan context
    structure_obj := structure_ind_obj(null, index_common.boolean_to_integer(finished),
                                 job_no, hit_list, similarity_list);
    structure_obj.rmi_hostname := rmi_hostname;

    -- get all remaining chunks
    structure_obj.build_score_table := 1;
    structure_obj.append_scores_to_table;
    structure_obj.fetch_all_chunks();

    return structure_obj.score_table;

    v_similarity_table.extend(hit_list.count);
    for cnt in 1..hit_list.count loop
        v_similarity_table(cnt) := similarity_hit(hit_list(cnt), similarity_list(cnt));
    end loop;

    return v_similarity_table;
end;

/*
    Binding to Java routine to extract smiles and fingerprints from index

*/
procedure tableIndexExtractSmiles (
    rmi_hostname varchar2,
    owner_name varchar2,
    table_name varchar2,
    column_name varchar2,
    sql_query varchar2,
    sql_update varchar2,
    sql_user varchar2,
    sql_password varchar2)
as language Java name
'com.cairn.rmi.oracle.Wrappers.tableIndexExtractSmiles
    (java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String,
     java.lang.String, java.lang.String, java.lang.String)';


/*
    Updates Oracle with the fingerprint and canonical smiles stored in the
    index.

    The query should return a rowid in column 1 and unique id in column 2.
    E.g.

    select rowid, molecule_id from molecules

    The update sql should take 3 bind parameters: smiles, fingerprint and
    unique id in that order. E.g.

    insert into mol_info (smiles, fingerprint, id) values (:1, :2, :3)

    or

    update molecules set canonical_smiles = :1, string_fingerprint = :2 where
    molecule_id = :3

   The sql will run using the provided user/password

*/
procedure tableIndexExtractSmiles (
    owner_name varchar2,
    table_name varchar2,
    column_name varchar2,
    sql_query varchar2,
    sql_update varchar2,
    sql_user varchar2,
    sql_password varchar2)
as
    rmi_hostname varchar2(1000);
begin

    rmi_hostname := index_common.get_rmi_hostname
        (structure_ind_obj.index_type_name, owner_name, table_name, column_name);
    tableIndexExtractSmiles(rmi_hostname, owner_name, table_name, column_name, sql_query,
                            sql_update, sql_user, sql_password);
end;

/*
   Given a rowid retrieves the smiles stored in the external index for
   that rowid.
*/
function tableIndexGetRowSmiles (
    rmi_hostname varchar2,
    owner_name varchar2,
    table_name varchar2,
    column_name varchar2,
    row_id varchar2)
return varchar2
as language Java name
'com.cairn.rmi.oracle.Wrappers.tableIndexGetRowSmiles
    (java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String)
    return java.lang.String';

/*
   Given a rowid retrieves the smiles stored in the external index for
   that rowid.
*/
function tableIndexGetRowSmiles (
    owner_name varchar2,
    table_name varchar2,
    column_name varchar2,
    row_id varchar2)
return varchar2
as
    rmi_hostname varchar2(1000);
begin
    rmi_hostname := index_common.get_rmi_hostname
        (structure_ind_obj.index_type_name, owner_name, table_name, column_name);
    return tableIndexGetRowSmiles(rmi_hostname, owner_name, table_name, column_name, row_id);
end;


/*
   Given a rowid retrieves the fingerprint stored in the external index for
   that rowid.
*/
function tableIndexGetRowFingerprint (
    rmi_hostname varchar2,
    owner_name varchar2,
    table_name varchar2,
    column_name varchar2,
    row_id varchar2)
return varchar2
as language Java name
'com.cairn.rmi.oracle.Wrappers.tableIndexGetRowFingerprint
    (java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String)
    return java.lang.String';

/*
   Given a rowid retrieves the fingerprint stored in the external index for
   that rowid.
*/
function tableIndexGetRowFingerprint (
    owner_name varchar2,
    table_name varchar2,
    column_name varchar2,
    row_id varchar2)
return varchar2
as
    rmi_hostname varchar2(1000);
begin
    rmi_hostname := index_common.get_rmi_hostname
        (structure_ind_obj.index_type_name, owner_name, table_name, column_name);
    return tableIndexGetRowFingerprint(rmi_hostname, owner_name, table_name,
                                       column_name, row_id);
end;

/*
   Given a id column name and an id retrieves the smiles stored in the external
   index for that id.  You can use this easily in SQL, whereas that's not the
   case with tableIndexGetRowSmiles.
*/
function tableIndexGetIdSmiles (
    owner_name varchar2,
    table_name varchar2,
    column_name varchar2,
    id_column_name varchar2,
    table_id varchar2)
return varchar2
as
    select_cmd varchar2(1000);
    row_v varchar2(1000);
    smiles varchar2(1000);
begin
    select_cmd := 'select rowid from '||owner_name ||'.'||table_name ||
        ' where '||id_column_name||' = :id';
    -- dbms_output.put_line('executing '||select_cmd);
    execute immediate select_cmd into row_v using table_id;
    if row_v is not null
    then
        smiles := tableIndexGetRowSmiles(owner_name, table_name,
            column_name, row_v);
        return smiles;
    end if;

    return null;
end;

/*
   Given a id columnname and an id retrieves the fingerprint stored in the external
   index for that id.  You can use this easily in SQL, whereas that's not the
   case with tableIndexGetRowFingerprint.
*/
function tableIndexGetIdFingerprint (
    owner_name varchar2,
    table_name varchar2,
    column_name varchar2,
    id_column_name varchar2,
    table_id varchar2)
return varchar2
as
    select_cmd varchar2(1000);
    row_v varchar2(1000);
    fp varchar2(2000);
begin
    select_cmd := 'select rowid from '||owner_name ||'.'||table_name ||
        ' where '||id_column_name||' = :id';
    --dbms_output.put_line('executing '||select_cmd);
    execute immediate select_cmd into row_v using table_id;
    if row_v is not null
    then
        fp := tableIndexGetRowFingerprint(owner_name, table_name,
            column_name, row_v);
        return fp;
    end if;

    return null;
end;

/*
   Given two rowids retrieves the pairwise Tanimoto score from the external index.
*/
function tableIndexGetRowSimilarity (
    rmi_hostname varchar2,
    owner_name varchar2,
    table_name varchar2,
    column_name varchar2,
    row_id1 varchar2,
    row_id2 varchar2)
return number
as language Java name
'com.cairn.rmi.oracle.Wrappers.tableIndexGetRowSimilarity
    (java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String)
    return double';

/*
   Given two rowids retrieves the pairwise Tanimoto score from the external index.
*/
function tableIndexGetRowSimilarity (
    owner_name varchar2,
    table_name varchar2,
    column_name varchar2,
    row_id1 varchar2,
    row_id2 varchar2)
return number
as
    rmi_hostname varchar2(1000);
begin
    rmi_hostname := index_common.get_rmi_hostname
        (structure_ind_obj.index_type_name, owner_name, table_name, column_name);
    return tableIndexGetRowSimilarity(rmi_hostname, owner_name, table_name,
                                      column_name, row_id1, row_id2);
end;

/*
   Given a id column name and two ids retrieves the Tanimoto score from the
   external RMI index.  You can use this easily in SQL, whereas that's not the
   case with tableIndexGetRowSimilarity.
*/
function tableIndexGetIdSimilarity (
    owner_name varchar2,
    table_name varchar2,
    column_name varchar2,
    id_column_name varchar2,
    table_id1 varchar2,
    table_id2 varchar2)
return number
as
    row1_v varchar2(1000);
    row2_v varchar2(1000);
    select_cmd varchar2(1000);
    similarity number;
begin
    select_cmd := 'select rowid from '||owner_name ||'.'||table_name ||
        ' where '||id_column_name||' = :id';
    --dbms_output.put_line('executing '||select_cmd);
    execute immediate select_cmd into row1_v using table_id1;
    execute immediate select_cmd into row2_v using table_id2;

    if row1_v is not null and row2_v is not null
    then
      similarity := tableIndexGetRowSimilarity(owner_name, table_name,
            column_name, row1_v, row2_v);
      return similarity;
    end if;

    return null;
end;


end;
/

create or replace type body structure_ind_obj as
/*
    Object to implement extensible indexing for smiles columns.

    Gareth Jones 3/08
*/

constructor function structure_ind_obj (
        index_info sys.ODCIINDEXINFO,
        finished integer,
        job_no integer,
        hitList rowIdArray)
return self as result
as
        v_key_name varchar2(1000);
begin
        self.finished := finished;
        self.job_no := job_no;
        self.hitlist := hitList;
        self.hitlist_position := 0;
        self.score_table := null;
        if index_info is not null then
            self.index_info := index_info;
            index_common.get_index_settings(index_info, structure_ind_obj.index_type_name(),
                self.rmi_hostname, v_key_name, self.index_lookup_id);
        end if;
        return;
end;

constructor function structure_ind_obj (
        index_info sys.ODCIINDEXINFO,
        finished integer,
        job_no integer,
        hitlist rowIdArray,
        similarity_list doubleArray)
return self as result
as
        v_key_name varchar2(1000);
begin
        self.finished := finished;
        self.job_no := job_no;
        self.hitlist := hitlist;
        self.hitlist_position := 0;
        self.scores := similarity_list;
        self.score_table := similarity_hits();
        self.similarity_ancillary := 0;
        if index_info is not null then
            self.index_info := index_info;
            index_common.get_index_settings(index_info, structure_ind_obj.index_type_name(),
                self.rmi_hostname, v_key_name, self.index_lookup_id);
        end if;
        return;
end;



/*
    Boilerplate code to show that we are using the ODCIIndex2 interface.
*/
STATIC FUNCTION ODCIGETINTERFACES(ifclist OUT nocopy SYS.ODCIOBJECTLIST)
RETURN NUMBER IS
BEGIN
       ifclist := SYS.ODCIOBJECTLIST(SYS.ODCIOBJECT('SYS','ODCIINDEX2'));
       RETURN odciconst.success;
END;

/*
    Creates the index. This does two things- build the java data-structure
    for the external RMI index and creates a table for recording row edits.
*/
STATIC FUNCTION ODCIINDEXCREATE
    (ia SYS.ODCIINDEXINFO, parms VARCHAR2, env SYS.ODCIEnv) RETURN NUMBER
is
    ok boolean;
    v_table_name varchar2(30);
    v_owner_name varchar2(30);
    v_column_name varchar2(30);
    v_key_name varchar2(100);
    ddl varchar2(1000);
    v_change_table_name varchar2(30);
    rmi_hostname varchar2(1000);
begin
    dbms_output.put_line('ODCIINDEXCREATE: creating index on '||v_table_name);
    v_key_name := index_common.schema_names(ia, v_owner_name, v_table_name, v_column_name);
    dbms_output.put_line('ODCIINDEXCREATE: index key name is '||v_key_name);

    --  create change table
    rmi_hostname := index_common.params_to_rmi_hostname(parms);
    v_change_table_name := index_common.create_lookup_and_change_table
        (index_type_name(), v_owner_name, v_table_name, v_column_name, v_key_name, ia.indexname, rmi_hostname);
    dbms_output.put_line('created log table c$cschem1.'||v_change_table_name ||' for '||
                          v_key_name);

    -- build external rmi index, must do this after creating the change table
    ok := index_utl.tableIndexOperation(rmi_hostname, v_owner_name, v_table_name,
                                             v_column_name, 'build',  parms);

    if not ok
    then
        raise_application_error(-20000, 'C$CSCHEM1-0001 rmi index build failed');
    end if;

    RETURN odciconst.success;
end;


/*
    Code to suport full and commit rebuilds, adding molecules to cache, table
    and column renames and index loading and unloading.
*/
STATIC FUNCTION ODCIIndexAlter (
    ia sys.ODCIIndexInfo,
    parms IN OUT nocopy VARCHAR2,
    altopt NUMBER,
    env sys.ODCIEnv)
RETURN NUMBER
is
    v_table_name varchar2(30);
    v_owner_name varchar2(30);
    v_column_name varchar2(30);
    v_key_name varchar2(100);
    new_key_name varchar2(100);
    old_obj_name varchar2(200);
    new_obj_name varchar2(200);
    v_change_table_name varchar2(100);
    ok boolean;
    sql_str varchar2(1000);
    new_column_name varchar2(30);
    v_index_type_name varchar2(100);
    rmi_hostname varchar2(1000);
begin

    v_key_name := index_common.schema_names(ia, v_owner_name, v_table_name, v_column_name);
    rmi_hostname := index_common.get_rmi_hostname(structure_ind_obj.index_type_name,  v_key_name);
    case altopt

    when odciconst.alterindexnone
    then

        if parms = 'unload'
        then
            dbms_output.put_line('Unloading '||v_key_name);
            ok := index_utl.tableIndexOperation(rmi_hostname, v_owner_name, v_table_name,
                                                v_column_name, 'unload');
            if not ok
            then
                raise_application_error(-20000,
                    'C$CSCHEM1-0019 alter index unload failed');
            end if;
        elsif parms = 'load'
        then
            dbms_output.put_line('Loading '||v_key_name);
            ok := index_utl.tableIndexOperation(rmi_hostname, v_owner_name, v_table_name,
                                                v_column_name, 'load');
            if not ok
            then
                raise_application_error(-20000,
                    'C$CSCHEM1-0020 alter index load failed');
            end if;
        elsif parms = 'add_to_cache'
        then
            dbms_output.put_line('Adding molecules to cache '||v_key_name);
            ok := index_utl.tableIndexOperation(rmi_hostname, v_owner_name, v_table_name,
                                                     v_column_name, 'add_to_cache');
            if not ok
            then
                raise_application_error(-20000,
                    'C$CSCHEM1-0021 alter index load failed');
            end if;
        else
            dbms_output.put_line('Unknown parameter '||parms);
        end if;

    when odciconst.alterindexrename
    then
        -- when the index is renamed we simply have to update the index_lookup table
        index_common.rename_index(parms, index_type_name, v_key_name);

    when odciconst.alterindexrebuild
    then
        if parms = 'full'
        then
            -- a full rebuild truncated the change table and rebuild the
            -- external index frokm scratch.
            dbms_output.put_line('Doing full rebuild');
            index_common.truncate_change_log_table(index_type_name, v_key_name);
            -- rebuild external rmi index from scratch
            ok := index_utl.tableIndexOperation(rmi_hostname, v_owner_name, v_table_name,
                                                v_column_name, 'build');
            if not ok
            then
                raise_application_error(-20000, 'C$CSCHEM1-0001 rmi index build failed');
            end if;

        else
            dbms_output.put_line('Doing normal rebuild - committing change log');
            -- the normal rebuild just commits the change log- this is done on the RMI server
            ok := index_utl.tableIndexOperation(rmi_hostname, v_owner_name, v_table_name, v_column_name, 'save');
            if not ok
            then
                raise_application_error(-20000,
                    'C$CSCHEM1-0014 alter index rebuild commit failed');
            end if;
        end if;

    when odciconst.AlterIndexRenameCol
    then
        -- to rename a column we need to update the index_lookup_table and the
        -- java__objects table and unload then load the external index.

        new_column_name := replace(parms, '"');
        new_key_name := v_owner_name || '.' || v_table_name || '.' || new_column_name;
        dbms_output.put_line('renaming index column from '
                            ||v_column_name||' to '||new_column_name);
        old_obj_name := v_key_name || '_indexInfo';
        new_obj_name := new_key_name || '_indexInfo';
        v_index_type_name := index_type_name;

        update index_lookup set index_key = new_key_name where index_key = v_key_name and index_type = v_index_type_name;
        update java_objects set name = new_obj_name where name = old_obj_name;
        commit;

        ok := index_utl.tableIndexOperation(rmi_hostname, v_owner_name, v_table_name, v_column_name, 'unload');
        if not ok
        then
            raise_application_error(-20000,
                'C$CSCHEM1-0017 alter index unload failed');
        end if;
           ok := index_utl.tableIndexOperation(rmi_hostname, v_owner_name, v_table_name, new_column_name, 'load');
        if not ok
        then
            raise_application_error(-20000,
                'C$CSCHEM1-0018 alter index load failed');
        end if;

    when odciconst.AlterIndexRenameTab
    then

        -- to rename a table we need to update the index_lookup_table and the
        -- java__objects table and unload then load the external index.
         dbms_output.put_line('renaming index table from '
                            ||v_table_name||' to '||parms);
        new_key_name := v_owner_name || '.' || parms || '.' || v_column_name;
        old_obj_name := v_key_name || '_indexInfo';
        new_obj_name := new_key_name || '_indexInfo';
        v_index_type_name := index_type_name;

        update index_lookup set index_key = new_key_name where index_key = v_key_name and index_type = v_index_type_name;
        update java_objects set name = new_obj_name where name = old_obj_name;
        commit;

        ok := index_utl.tableIndexOperation(rmi_hostname, v_owner_name, v_table_name,
                                                 v_column_name, 'unload');
        if not ok
        then
            raise_application_error(-20000,
                'C$CSCHEM1-0015 alter index unload failed');
        end if;
           ok := index_utl.tableIndexOperation(rmi_hostname, v_owner_name, parms,
                                                    v_column_name, 'load');
        if not ok
        then
            raise_application_error(-20000,
                'C$CSCHEM1-0016 alter index load failed');
        end if;

    when odciconst.AlterIndexUpdBlockRefs
    then
        -- "alter table update block references" what does this do?
        null;

    else
        return odciconst.error;

    end case;

    RETURN odciconst.success;
end;

/*
    Truncates index by empting the rmi index and saving to Oracle.
    The change log table is truncated.
*/
STATIC FUNCTION ODCIINDEXTRUNCATE (
        ia SYS.ODCIINDEXINFO,
        env SYS.ODCIEnv)
RETURN NUMBER
is
        v_key_name varchar2(100);
        v_table_name varchar2(30);
        v_owner_name varchar2(30);
        v_column_name varchar2(30);
        rmi_hostname varchar2(1000);
        ok boolean;
begin
        v_key_name := index_common.schema_names(ia, v_owner_name, v_table_name, v_column_name);
        rmi_hostname := index_common.get_rmi_hostname(structure_ind_obj.index_type_name,  v_key_name);

        -- truncat external rmi index
        ok := index_utl.tableIndexOperation(rmi_hostname, v_owner_name, v_table_name,
                                                 v_column_name, 'truncate');
        if not ok
        then
            raise_application_error(-20000, 'C$CSCHEM1-0004 rmi index truncate failed');
        end if;

        --  create change table
        index_common.truncate_change_log_table(index_type_name, v_key_name);
        dbms_output.put_line('truncated log table for ' || v_key_name);

        RETURN odciconst.success;
end;

/*
    Drops index by removing rmi index from server and Oracle.
    Removes change log table and lookup entry.
*/
STATIC FUNCTION ODCIINDEXDROP(
        ia SYS.ODCIINDEXINFO,
        env SYS.ODCIEnv) RETURN NUMBER
is
        v_key_name varchar2(100);
        v_table_name varchar2(30);
        v_owner_name varchar2(30);
        v_column_name varchar2(30);
        rmi_hostname varchar2(1000);
        ok boolean;
begin
        dbms_output.put_line('dropping index');
        v_key_name := index_common.schema_names(ia, v_owner_name, v_table_name, v_column_name);
        rmi_hostname := index_common.get_rmi_hostname(structure_ind_obj.index_type_name,  v_key_name);

        -- drop external rmi index
        begin
            ok := index_utl.tableIndexOperation(rmi_hostname, v_owner_name, v_table_name,
                                                 v_column_name, 'drop');
        exception
        when others then
            ok := false;
        end;

        -- if the rmi server has failed to drop the index properly continue with the
        -- clean-up here and return a warning.  This may leave some some data behind that requires manual cleaning
        -- but should result in a state whereby the table is free from locks and the index can be created again if needbe.
        -- Otherwise the index will be marked as FAILED and will need to be force dropped and manual cleaning may be
        -- required before an index can be put on the table.

        if not ok
        then
          dbms_output.put_line('rmi server '||rmi_hostname||' failed to drop index correctly '||
                    'there may be unused data in c$cschem1.java_objects');
            -- raise_application_error(-20000, 'C$CSCHEM1-0005 rmi index drop failed');
        end if;

        --  drop change table
        index_common.drop_change_log_table(index_type_name, v_key_name);
        dbms_output.put_line('dropped log table for ' || v_key_name);

        RETURN odciconst.success;
end;

/*
    A new value has been added to the index column.
    Put it in the change log table. For varchar2 columns.
*/
STATIC FUNCTION ODCIINDEXINSERT(
        ia SYS.ODCIINDEXINFO,
        rid ROWID,
        newval varchar2,
        env SYS.ODCIEnv)
RETURN NUMBER
is
begin
        return index_common.index_insert(index_type_name(), ia, rid, newval);
end;
/*
    A new value has been added to the index column.
    Put it in the change log table. For clob columns.
*/
STATIC FUNCTION ODCIINDEXINSERT(
        ia SYS.ODCIINDEXINFO,
        rid ROWID,
        newval clob,
        env SYS.ODCIEnv)
RETURN NUMBER
is
begin
        return index_common.index_insert(index_type_name(), ia, rid, newval);
end;
/*
    A new value has been added to the index column.
    Put it in the change log table. For blob columns.
*/
STATIC FUNCTION ODCIINDEXINSERT(
        ia SYS.ODCIINDEXINFO,
        rid ROWID,
        newval blob,
        env SYS.ODCIEnv)
RETURN NUMBER
is
begin
        return index_common.index_insert(index_type_name(), ia, rid, newval);
end;

/*
    An old value has been deleted from the index column.
    Put it in the change log table.  For varchar2 columns.
*/
STATIC FUNCTION ODCIINDEXDELETE(
        ia SYS.ODCIINDEXINFO,
        rid ROWID,
        oldval varchar2,
        env SYS.ODCIEnv)
RETURN NUMBER  is
begin
        return index_common.index_delete(index_type_name(), ia, rid, oldval);
end;

/*
    An old value has been deleted from the index column.
    Put it in the change log table.  For clob columns.
*/
STATIC FUNCTION ODCIINDEXDELETE(
        ia SYS.ODCIINDEXINFO,
        rid ROWID,
        oldval clob,
        env SYS.ODCIEnv)
RETURN NUMBER  is
begin
        return index_common.index_delete(index_type_name(), ia, rid, oldval);
end;

/*
    An old value has been deleted from the index column.
    Put it in the change log table.  For blob columns.
*/
STATIC FUNCTION ODCIINDEXDELETE(
        ia SYS.ODCIINDEXINFO,
        rid ROWID,
        oldval blob,
        env SYS.ODCIEnv)
RETURN NUMBER  is
begin
        return index_common.index_delete(index_type_name(), ia, rid, oldval);
end;


/*
    A row has been edited in the index column.
    Put it in the change log table.  For varchar2 columns.
*/
STATIC FUNCTION ODCIINDEXUPDATE(
        ia SYS.ODCIINDEXINFO,
        rid ROWID,
        oldval varchar2,
        newval varchar2,
        env SYS.ODCIEnv)
RETURN NUMBER  is
begin
        return index_common.index_update(index_type_name(), ia, rid, oldval, newval);
end;

/*
    A row has been edited in the index column.
    Put it in the change log table.  For clob columns.
*/
STATIC FUNCTION ODCIINDEXUPDATE(
        ia SYS.ODCIINDEXINFO,
        rid ROWID,
        oldval clob,
        newval clob,
        env SYS.ODCIEnv)
RETURN NUMBER  is
begin
        return index_common.index_update(index_type_name(), ia, rid, oldval, newval);
end;

/*
    A row has been edited in the index column.
    Put it in the change log table.  For blob columns.
*/
STATIC FUNCTION ODCIINDEXUPDATE(
        ia SYS.ODCIINDEXINFO,
        rid ROWID,
        oldval blob,
        newval blob,
        env SYS.ODCIEnv)
RETURN NUMBER  is
begin
        return index_common.index_update(index_type_name(), ia, rid, oldval, newval);
end;


/*
    Matches exact match and substructure
*/
STATIC FUNCTION ODCIINDEXSTART(
        sctx IN OUT nocopy structure_ind_obj,
        ia SYS.ODCIINDEXINFO,
        op SYS.ODCIPREDINFO,
        qi SYS.ODCIQUERYINFO,
        strt NUMBER,
        stop NUMBER,
        query varchar2,
        maxHits integer,
        env SYS.ODCIEnv)
RETURN NUMBER  is
        v_table_name varchar2(30);
        v_owner_name varchar2(30);
        v_column_name varchar2(30);
        v_key_name varchar2(100);
        hit_list rowIdArray;
        finished boolean;
        finished_int integer;
        job_no integer;
        rmi_hostname varchar2(1000);
begin

        dbms_output.put_line('ODCIIndexStart>>>>>');
        --sys.ODCIIndexInfoDump(ia);
        sys.ODCIPredInfoDump(op);
        dbms_output.put_line('start key : '||strt);
        dbms_output.put_line('stop key : '||stop);
        dbms_output.put_line('<<<<<');

        v_key_name := index_common.schema_names
                (ia, v_owner_name, v_table_name, v_column_name);
        rmi_hostname := index_common.get_rmi_hostname(structure_ind_obj.index_type_name, v_key_name);
        hit_list := rowIdArray();


        case op.ObjectName

        when 'SUBSTRUCTURE'
        then
            -- some checks
            if strt != 1 or
                (bitand(op.Flags, ODCIConst.PredExactMatch) != ODCIConst.PredExactMatch)
            then
                raise_application_error(-20006, 'C$CSCHEM1-0006 substructure search predicate malformed');
            end if;

            -- do substructure search
            dbms_output.put_line
                ('doing index search substructure search');
            dbms_output.put_line('Predicate info '|| op.objectschema || '.' ||
                                  op.objectname || '.' || op.methodname);

            index_utl.indexSubstructureSearch
                (rmi_hostname, finished, false, job_no, v_owner_name, v_table_name, v_column_name,
                 query, hit_list, maxHits, true, 'smarts');

        when 'MDL_SUBSTRUCTURE'
        then
            -- some checks
            if strt != 1 or
                (bitand(op.Flags, ODCIConst.PredExactMatch) != ODCIConst.PredExactMatch)
            then
                raise_application_error(-20006, 'C$CSCHEM1-0022 substructure search predicate malformed');
            end if;

            -- do substructure search
            dbms_output.put_line
                ('doing index search mdl substructure search');
            dbms_output.put_line('Predicate info '|| op.objectschema || '.' ||
                                  op.objectname || '.' || op.methodname);

            index_utl.indexSubstructureSearch
                (rmi_hostname, finished, false, job_no, v_owner_name, v_table_name, v_column_name,
                 query, hit_list, maxHits, true, 'mdl');


        when 'EXACTMATCH'
        then
            -- some checks
            if strt != 1 or
                (bitand(op.Flags, ODCIConst.PredExactMatch) != ODCIConst.PredExactMatch)
            then
                raise_application_error(-20006, 'C$CSCHEM1-0007 exact match predicate malformed');
            end if;

            -- first do substructure search
            dbms_output.put_line
                ('doing index exact match search');

            index_utl.indexExactMatchSearch
                (rmi_hostname, finished, false, job_no, v_owner_name, v_table_name, v_column_name,
                 query, hit_list, maxHits);

        else
            raise_application_error(-20006, 'C$CSCHEM1-0005 uknown operator '||op.ObjectName);
        end case;

        -- save hitlist in scan context
        finished_int := index_common.boolean_to_integer(finished);
        sctx := structure_ind_obj(ia, finished_int, job_no, hit_list);

        RETURN odciconst.success;

end;



/*
    Matches MDL substructure, with CLOB query
*/
STATIC FUNCTION ODCIINDEXSTART(
        sctx IN OUT nocopy structure_ind_obj,
        ia SYS.ODCIINDEXINFO,
        op SYS.ODCIPREDINFO,
        qi SYS.ODCIQUERYINFO,
        strt NUMBER,
        stop NUMBER,
        query clob,
        maxHits integer,
        env SYS.ODCIEnv)
RETURN NUMBER  is
        v_table_name varchar2(30);
        v_owner_name varchar2(30);
        v_column_name varchar2(30);
        v_key_name varchar2(100);
        hit_list rowIdArray;
        finished boolean;
        finished_int integer;
        job_no integer;
        rmi_hostname varchar2(1000);
        opName varchar2(30);
begin

        dbms_output.put_line('ODCIIndexStart>>>>>');
        --sys.ODCIIndexInfoDump(ia);
        sys.ODCIPredInfoDump(op);
        dbms_output.put_line('start key : '||strt);
        dbms_output.put_line('stop key : '||stop);
        dbms_output.put_line('<<<<<');

        v_key_name := index_common.schema_names
                (ia, v_owner_name, v_table_name, v_column_name);
        rmi_hostname := index_common.get_rmi_hostname(structure_ind_obj.index_type_name, v_key_name);

        hit_list := rowIdArray();
        opName := op.ObjectName;

        if opName = 'MDL_CLOB_SUBSTRUCTURE' or opName = 'MDL_SUBSTRUCTURE' or opName = 'SUBSTRUCTURE'
        then

            -- some checks
            if strt != 1 or
                (bitand(op.Flags, ODCIConst.PredExactMatch) != ODCIConst.PredExactMatch)
            then
                raise_application_error(-20006, 'C$CSCHEM1-0025 substructure search predicate malformed');
            end if;

            -- do substructure search
            dbms_output.put_line
                ('doing index search mdl substructure search');
            dbms_output.put_line('Predicate info '|| op.objectschema || '.' ||
                                  op.objectname || '.' || op.methodname);

            index_utl.indexClobSubstructureSearch
                (rmi_hostname, finished, false, job_no, v_owner_name, v_table_name,
                v_column_name, query, hit_list, maxHits, true, 'mdl');

        else
            raise_application_error(-20006, 'C$CSCHEM1-0005 uknown operator '||op.ObjectName);
        end if;

        -- save hitlist in scan context
        finished_int := index_common.boolean_to_integer(finished);
        sctx := structure_ind_obj(ia, finished_int, job_no, hit_list);

        RETURN odciconst.success;

end;

/*
    Matches similarity search.
*/
STATIC FUNCTION ODCIINDEXSTART(
        sctx IN OUT nocopy structure_ind_obj,
        ia SYS.ODCIINDEXINFO,
        op SYS.ODCIPREDINFO,
        qi SYS.ODCIQUERYINFO,
        strt NUMBER,
        stop NUMBER,
        query varchar2,
        minSimilarity number,
        maxHits integer,
        env SYS.ODCIEnv)
RETURN NUMBER  is
        v_table_name varchar2(30);
        v_owner_name varchar2(30);
        v_column_name varchar2(30);
        v_key_name varchar2(100);
        hit_list rowIdArray;
        similarity_list doubleArray;
        finished boolean;
        finished_int integer;
        job_no integer;
        similarity_ancillary boolean := false;
        rmi_hostname varchar2(1000);
begin

        dbms_output.put_line('ODCIIndexStart>>>>>');
        --sys.ODCIIndexInfoDump(ia);
        sys.ODCIPredInfoDump(op);
        dbms_output.put_line('start key : '||strt);
        dbms_output.put_line('stop key : '||stop);
        dbms_output.put_line('<<<<<');

        v_key_name := index_common.schema_names
                (ia, v_owner_name, v_table_name, v_column_name);
        rmi_hostname := index_common.get_rmi_hostname(structure_ind_obj.index_type_name, v_key_name);

        hit_list := rowIdArray();

        case op.ObjectName

        when 'SIMILARITY'
        then
            -- some checks
            if strt != 1 or
                (bitand(op.Flags, ODCIConst.PredExactMatch) != ODCIConst.PredExactMatch)
            then
                raise_application_error(-20008, 'C$CSCHEM1-0008 similarity search predicate malformed');
            end if;

            -- check to see if the acillary operator is present in the query.
            if qi.ancops is not null then
                similarity_ancillary := true;
                dbms_output.put_line('ancillary operator present');
            end if;

            -- do similarity search
            dbms_output.put_line
                ('doing index search similarity search');

            index_utl.indexSimilaritySearch
                (rmi_hostname, finished, similarity_ancillary, job_no, v_owner_name,
                 v_table_name, v_column_name, query,
                 minSimilarity, hit_list, similarity_list, maxHits);

        else
            raise_application_error(-20006, 'C$CSCHEM1-0009 uknown operator '||op.ObjectName);
        end case;

        -- save hitlist in scan contex
        finished_int := index_common.boolean_to_integer(finished);
        sctx := structure_ind_obj(ia, finished_int, job_no, hit_list, similarity_list);

        if similarity_ancillary then
            sctx.similarity_ancillary := 1;
            index_base_obj.append_scores_to_table(sctx);
        end if;


        RETURN odciconst.success;

end;

/*
      Matches extended similarity search which allows specification of search
      method and fingerprint type.
*/
STATIC FUNCTION ODCIINDEXSTART(
        sctx IN OUT nocopy structure_ind_obj,
        ia SYS.ODCIINDEXINFO,
        op SYS.ODCIPREDINFO,
        qi SYS.ODCIQUERYINFO,
        strt NUMBER,
        stop NUMBER,
        fingerprint_type varchar2,
        search_method varchar2,
        query varchar2,
        minSimilarity number,
        maxHits integer,
        arg1 number,
        arg2 number,
        env SYS.ODCIEnv)
return number as
        v_table_name varchar2(30);
        v_owner_name varchar2(30);
        v_column_name varchar2(30);
        v_key_name varchar2(100);
        hit_list rowIdArray;
        similarity_list doubleArray;
        finished boolean;
        finished_int integer;
        job_no integer;
        similarity_ancillary boolean := false;
        rmi_hostname varchar2(1000);
begin
        dbms_output.put_line('ODCIIndexStart>>>>>');
        --sys.ODCIIndexInfoDump(ia);
        sys.ODCIPredInfoDump(op);
        dbms_output.put_line('start key : '||strt);
        dbms_output.put_line('stop key : '||stop);
        dbms_output.put_line('<<<<<');

        v_key_name := index_common.schema_names
                (ia, v_owner_name, v_table_name, v_column_name);
        rmi_hostname := index_common.get_rmi_hostname(structure_ind_obj.index_type_name, v_key_name);

        hit_list := rowIdArray();

        case op.ObjectName

        when 'EXTENDED_SIMILARITY'
        then
            -- some checks
            if strt != 1 or
                (bitand(op.Flags, ODCIConst.PredExactMatch) != ODCIConst.PredExactMatch)
            then
                raise_application_error(-20008,
                       'C$CSCHEM1-0008 similarity search predicate malformed');
            end if;

            -- check to see if the acillary operator is present in the query.
            if qi.ancops is not null then
                similarity_ancillary := true;
                dbms_output.put_line('ancillary operator present');
            end if;

            -- do similarity search
            dbms_output.put_line
                ('doing index extended similarity search');

            index_utl.extendedSimilarityIndexSearch
                (rmi_hostname, finished, similarity_ancillary, job_no, v_owner_name, v_table_name,
                v_column_name, fingerprint_type, search_method, query,
                minSimilarity, hit_list, similarity_list, maxHits, arg1, arg2);

            dbms_output.put_line
                ('finished index extended similarity search');
        else
            raise_application_error(-20006, 'C$CSCHEM1-0009 uknown operator '||
                                    op.ObjectName);
        end case;

        -- save hitlist in scan contex
        finished_int := index_common.boolean_to_integer(finished);
        dbms_output.put_line
                ('saving hitlist in scan context');
        sctx := structure_ind_obj(ia, finished_int, job_no, hit_list, similarity_list);

        if similarity_ancillary then
            sctx.similarity_ancillary := 1;
        end if;

        dbms_output.put_line('returning success');
        RETURN odciconst.success;
end ODCIINDEXSTART;

/*
    Fetches a batch of rowids from the hitlist
*/
MEMBER FUNCTION ODCIINDEXFETCH(
        SELF IN OUT nocopy structure_ind_obj,
        nrows NUMBER,
        rids OUT nocopy SYS.ODCIRIDLIST,
        env SYS.ODCIEnv)
RETURN NUMBER  is
begin
        dbms_output.put_line('ODCIIndexFetch>>>>>');
        return index_base_obj.fetch_row_ids(self, nrows, rids);
end;

/*
    closes the index.  Nothing to do.
*/
MEMBER FUNCTION ODCIINDEXCLOSE(
        SELF IN OUT nocopy structure_ind_obj,
        env SYS.ODCIEnv)
RETURN NUMBER  is
begin
        if similarity_ancillary = 1 then
            dbms_output.put_line('cleaning similarity scores from java cache');
            index_common.remove_scores(rmi_hostname, job_no);
        end if;
        return odciconst.success;
end;

/*
    Returns the index type name
*/
static function index_type_name
return varchar2
is
begin
    return 'table_index';
end;

end;
/

create or replace package body index_utl as

/*
    Some utilities for helping managing the structure index.

    Included here are the functional operators which are used for search on a
    domain index where a full scan is not done (I've never known this to
    happen, so I can't say for sure that these functions work) and also for
    search where a domain index is not present (these work but are likey to
    create resource issues).

    Gareth Jones 3/08
*/


/*
    Wrapper for java function that applies a table operation
*/
function tableIndexOperation (
        rmi_hostname varchar2,
        owner_name varchar2,
        table_name varchar2,
        column_name varchar2,
        operation varchar2)
return  boolean
as language Java name
    'com.cairn.rmi.oracle.Wrappers.tableIndexOperation
        (java.lang.String, java.lang.String, java.lang.String, java.lang.String,
        java.lang.String) return boolean';


/*
    Wrapper for java function that applies a table operation
*/
function tableIndexOperation (
        rmi_hostname varchar2,
        owner_name varchar2,
        table_name varchar2,
        column_name varchar2,
        operation varchar2,
        params varchar2)
return  boolean
as language Java name
    'com.cairn.rmi.oracle.Wrappers.tableIndexOperation
        (java.lang.String, java.lang.String, java.lang.String, java.lang.String,
        java.lang.String, java.lang.String) return boolean';
        
/*
    Functional implementation for substructure search

    Note the functional implementation is always slower than the full index scan.
*/
function substructSearchFunction (
    query varchar2,
    maxHits integer,
    query_type varchar2,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number
as
    cnt integer;
    v_table_name varchar2(30);
    v_owner_name varchar2(30);
    v_column_name varchar2(30);
    v_key_name varchar2(100);
    hit_list rowIdArray;
    finished boolean;
    is_hit boolean;
    job_no integer;
    rmi_hostname varchar(1000);
begin

    -- dbms_output.put_line('scanflg is '||scanflg);

    if scanflg = 1
    then
       -- clean up java cache
       index_common.remove_scores(scanctx.rmi_hostname, scanctx.job_no);
       return 0;
    end if;


    -- dbms_output.put_line('Functional seach query is '||query);

    if indexctx is null or indexctx.indexinfo is null
    then
        -- no domain index present
        raise_application_error
                (-20000,
                'C$CSCHEM1-0003 functional substructure search requires domain index');
        return 0;

    end if;

    -- use domain index

    if scanctx is null
    then
        -- this is the first call to the function.

        -- first do substructure search
        dbms_output.put_line
             ('doing functional implementation for substructure search');
        v_key_name := index_common.schema_names
             (indexctx.indexinfo, v_owner_name, v_table_name, v_column_name);
        rmi_hostname := index_common.get_rmi_hostname(structure_ind_obj.index_type_name, v_key_name);
        hit_list := rowIdArray();
        index_utl.indexSubstructureSearch
                (rmi_hostname, finished, true, job_no, v_owner_name, v_table_name, v_column_name,
                 query, hit_list, maxHits, true, query_type);

        -- save hitlist in scan context
        scanctx := structure_ind_obj(indexctx.indexinfo, index_common.boolean_to_integer(finished),
                                     job_no, hit_list);
        -- get all remaining chunks
        index_base_obj.fetch_all_chunks(scanctx);

    end if;

    -- determine if the current row is a hit by checking java cache map
    is_hit := index_common.is_hit(scanctx.rmi_hostname, scanctx.job_no, indexctx.rid);
    return index_common.boolean_to_integer(is_hit);
end;

/*
    Functional implementation for substructure search, with clob query

    Note the functional implementation is always slower than the full index scan.
*/
function substructClobSearchFunction (
    query clob,
    maxHits integer,
    query_type varchar2,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number
as
    cnt integer;
    v_table_name varchar2(30);
    v_owner_name varchar2(30);
    v_column_name varchar2(30);
    v_key_name varchar2(100);
    hit_list rowIdArray;
    finished boolean;
    is_hit boolean;
    job_no integer;
    rmi_hostname varchar(1000);
begin

    if scanflg = 1
    then
       -- clean up java cache
       index_common.remove_scores(scanctx.rmi_hostname, scanctx.job_no);
       return 0;
    end if;

    if indexctx is null or indexctx.indexinfo is null
    then
        -- no domain index present
        raise_application_error
                (-20000,
                'C$CSCHEM1-0024 functional clob search requires domain index');
        return 0;

    end if;

    if scanctx is null
    then
        -- this is the first call to the function.

        -- first do substructure search
        dbms_output.put_line
            ('doing functional implementation for clob substructure search');
        v_key_name := index_common.schema_names
            (indexctx.indexinfo, v_owner_name, v_table_name, v_column_name);
        rmi_hostname := index_common.get_rmi_hostname(structure_ind_obj.index_type_name, v_key_name);
        hit_list := rowIdArray();
        index_utl.indexClobSubstructureSearch
            ( rmi_hostname, finished, true, job_no, v_owner_name, v_table_name, v_column_name,
             query, hit_list, maxHits, true, query_type);

           -- save hitlist in scan context
        scanctx := structure_ind_obj(indexctx.indexinfo, index_common.boolean_to_integer(finished),
                                     job_no, hit_list);
        -- get all remaining chunks
        index_base_obj.fetch_all_chunks(scanctx);

    end if;

    -- determine if the current row is a hit by checking java cache map
    is_hit := index_common.is_hit(scanctx.rmi_hostname, scanctx.job_no, indexctx.rid);
    return index_common.boolean_to_integer(is_hit);

end;

/*
    Functional implementation for substructure search with smarts query.
    For varchar2 column
 */
function substructureSearchFunction (
    smiles varchar2,
    query varchar2,
    maxHits integer,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number
as
begin
  return substructSearchFunction(query, maxHits, 'smarts', indexctx, scanctx, scanflg);
end;

/*
    Functional implementation for substructure search with smarts query.
    For clob column
 */
function substructureSearchFunction (
    sdf clob,
    query varchar2,
    maxHits integer,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number
as
begin
  return substructSearchFunction(query, maxHits, 'smarts', indexctx, scanctx, scanflg);
end;

/*
    Functional implementation for substructure search with smarts query.
    For blob column
 */
function substructureSearchFunction (
    oemol blob,
    query varchar2,
    maxHits integer,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number
as
begin
  return substructSearchFunction(query, maxHits, 'smarts', indexctx, scanctx, scanflg);
end;

/*
    Functional implementation for substructure search with mdl query as a clob.
    For a varchar2 column.
 */
function mdlSubstructClobSearchFunction (
    smiles varchar2,
    query clob,
    maxHits integer,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number
as
begin
  return substructClobSearchFunction(query, maxHits, 'mdl', indexctx, scanctx, scanflg);
end;

/*
    Functional implementation for substructure search with mdl query as a clob.
    For a clob column.
 */
function mdlSubstructClobSearchFunction (
    sdf clob,
    query clob,
    maxHits integer,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number
as
begin
  return substructClobSearchFunction(query, maxHits, 'mdl', indexctx, scanctx, scanflg);
end;

/*
    Functional implementation for substructure search with mdl query as a clob.
    For a blob column.
 */
function mdlSubstructClobSearchFunction (
    oemol blob,
    query clob,
    maxHits integer,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number
as
begin
  return substructClobSearchFunction(query, maxHits, 'mdl', indexctx, scanctx, scanflg);
end;

/*
    Functional implementation for substructure search with mdl query
    For a varchar2 column.
 */
function mdlSubstructureSearchFunction (
    smiles varchar2,
    query varchar2,
    maxHits integer,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number
as
begin
  return substructSearchFunction(query, maxHits, 'mdl', indexctx, scanctx, scanflg);
end;

/*
    Functional implementation for substructure search with mdl query
    For a clob column.
 */
function mdlSubstructureSearchFunction (
    sdf clob,
    query varchar2,
    maxHits integer,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number
as
begin
  return substructSearchFunction(query, maxHits, 'mdl', indexctx, scanctx, scanflg);
end;

/*
    Functional implementation for substructure search with mdl query
    For a blob column.
 */
function mdlSubstructureSearchFunction (
    oemol blob,
    query varchar2,
    maxHits integer,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number
as
begin
  return substructSearchFunction(query, maxHits, 'mdl', indexctx, scanctx, scanflg);
end;

/*
    Functional implementation for exact match.
    Common code for all column types

    Note the functional implementation is always slower than the full index scan.
*/
function exactMatchSearchFunction (
    query varchar2,
    maxHits integer,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number
as
    cnt integer;
    v_table_name varchar2(30);
    v_owner_name varchar2(30);
    v_column_name varchar2(30);
    v_key_name varchar2(100);
    hit_list rowIdArray;
    finished boolean;
    is_hit boolean;
    job_no integer;
    rmi_hostname varchar2(1000);
begin

    if scanflg = 1
    then
       -- clean up java cache
       index_common.remove_scores(scanctx.rmi_hostname, scanctx.job_no);
       return 0;
    end if;

    if indexctx is null or indexctx.indexinfo is null
    then
        -- no domain index present

        -- dbms_output.put_line
        --       ('doing functional implementation for exact match search: no domain index present');

        raise_application_error
                (-20000,
                'C$CSCHEM1-0010 functional exact match requires domain index');
        return 0;

        -- is_hit := chem_structure.funcExactMatchSearch(smiles, query);
        -- return index_common.boolean_to_integer(is_hit);

    end if;

    if scanctx is null
    then
        -- this is the first call to the function.

        -- first do substructure search
        dbms_output.put_line('doing functional implementation for exact match search');
        v_key_name := index_common.schema_names
            (indexctx.indexinfo, v_owner_name, v_table_name, v_column_name);
        rmi_hostname := index_common.get_rmi_hostname(structure_ind_obj.index_type_name, v_key_name);
        hit_list := rowIdArray();
        index_utl.indexExactMatchSearch
            (rmi_hostname, finished, true, job_no, v_owner_name, v_table_name, v_column_name,
             query, hit_list, maxHits);

        -- save hitlist in scan context
        scanctx := structure_ind_obj(indexctx.indexinfo, index_common.boolean_to_integer(finished),
                                     job_no, hit_list);
        -- get all remaining chunks
        index_base_obj.fetch_all_chunks(scanctx);

    end if;

    -- determine if the current row is a hit by checking java cache map
    is_hit := index_common.is_hit(scanctx.rmi_hostname, scanctx.job_no, indexctx.rid);
    return index_common.boolean_to_integer(is_hit);

end;


/*
    Functional implementation for exact match.
    For varchar2 column
*/
function exactMatchSearchFunction (
    smiles varchar2,
    query varchar2,
    maxHits integer,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number
as
begin
  return exactMatchSearchFunction( query, maxHits, indexctx, scanctx, scanflg);
end;

/*
    Functional implementation for exact match.
    For clob column
*/
function exactMatchSearchFunction (
    sdf clob,
    query varchar2,
    maxHits integer,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number
as
begin
  return exactMatchSearchFunction( query, maxHits, indexctx, scanctx, scanflg);
end;

/*
    Functional implementation for exact match.
    For blob column
*/
function exactMatchSearchFunction (
    oemol blob,
    query varchar2,
    maxHits integer,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number
as
begin
  return exactMatchSearchFunction( query, maxHits, indexctx, scanctx, scanflg);
end;



/*
    Functional implementation for similarity search. Common code for all column types.

    Note the functional implementation is always slower than the full index
    scan.  Need to create an associative array of hits and store it.
*/
function similaritySearchFunction (
    query varchar2,
    minSimilarity number,
    maxHits integer,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number
as
    cnt integer;
    v_table_name varchar2(30);
    v_owner_name varchar2(30);
    v_column_name varchar2(30);
    v_key_name varchar2(100);
    hit_list rowIdArray;
    similarity_list doubleArray;
    finished boolean;
    is_hit boolean;
    job_no integer;
    rmi_hostname varchar2(1000);
begin

    if scanflg = 1
    then
       -- clean up java cache
       index_common.remove_scores(scanctx.rmi_hostname, scanctx.job_no);
       return 0;
    end if;

    if indexctx is null or indexctx.indexinfo is null
    then
        -- no domain index present

        raise_application_error
                (-20000,
                'C$CSCHEM1-0011 functional similarity search requires domain index');
        return 0;


    end if;

    if scanctx is null
    then
        -- this is the first call to the function.

        -- first do substructure search
        dbms_output.put_line
            ('doing functional implementation for similarity search');
        v_key_name := index_common.schema_names
            (indexctx.indexinfo, v_owner_name, v_table_name, v_column_name);
        rmi_hostname := index_common.get_rmi_hostname(structure_ind_obj.index_type_name, v_key_name);
        hit_list := rowIdArray();
        index_utl.indexSimilaritySearch
            (rmi_hostname, finished, true, job_no, v_owner_name, v_table_name, v_column_name,
             query, minSimilarity,
             hit_list, similarity_list, maxHits);

        -- save hitlist in scan context
        scanctx := structure_ind_obj(indexctx.indexinfo, index_common.boolean_to_integer(finished),
                                     job_no, hit_list);
        -- get all remaining chunks
        index_base_obj.fetch_all_chunks(scanctx);

    end if;

    -- determine if the current row is a hit by checking java cache map
    is_hit := index_common.is_hit(scanctx.rmi_hostname, scanctx.job_no, indexctx.rid);
    return index_common.boolean_to_integer(is_hit);

end;

/*
    Functional implementation for similarity search. For varchar2 columns
*/
function similaritySearchFunction (
    smiles varchar2,
    query varchar2,
    minSimilarity number,
    maxHits integer,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number
as
begin
  return similaritySearchfunction(query, minSimilarity, maxHits, indexctx, scanctx, scanflg);
end;

/*
    Functional implementation for similarity search. For clob columns
*/
function similaritySearchFunction (
    sdf clob,
    query varchar2,
    minSimilarity number,
    maxHits integer,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number
as
begin
  return similaritySearchfunction(query, minSimilarity, maxHits, indexctx, scanctx, scanflg);
end;

/*
    Functional implementation for similarity search. For blob columns
*/
function similaritySearchFunction (
    oemol blob,
    query varchar2,
    minSimilarity number,
    maxHits integer,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number
as
begin
  return similaritySearchfunction(query, minSimilarity, maxHits, indexctx, scanctx, scanflg);
end;

/*
    Ancillary function to find similarity score by row id.  For varchar2 column
*/
function similarityScoreFunction (
    smiles varchar2,
    query varchar2,
    minSimilarity number,
    maxHits integer,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number
as
begin

    -- get the similarity score from a java cache
    return index_common.retrieve_score(scanctx.rmi_hostname, scanctx.job_no, indexctx.rid);

end;

/*
    Ancillary function to find similarity score by row id.  For clob column
*/
function similarityScoreFunction (
    sdf clob,
    query varchar2,
    minSimilarity number,
    maxHits integer,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number
as
begin

    -- get the similarity score from a java cache
    return index_common.retrieve_score(scanctx.rmi_hostname, scanctx.job_no, indexctx.rid);

end;

/*
    Ancillary function to find similarity score by row id.  For blob column
*/
function similarityScoreFunction (
    oeMol blob,
    query varchar2,
    minSimilarity number,
    maxHits integer,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number
as
begin

    -- get the similarity score from a java cache
    return index_common.retrieve_score(scanctx.rmi_hostname, scanctx.job_no, indexctx.rid);

end;

/*
  Ancillary funciton for smiles column and extended similarity search
 */
function similarityScoreFunction (
    smiles varchar2,
    fingerprint_type varchar2,
    search_method varchar2,
    query varchar2,
    minSimilarity number,
    maxHits integer,
    arg1 number,
    arg2 number,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number as
    hit_list rowIdArray;
    cnt integer;
    v_score number;
begin

    -- get the similarity score from a java cache
    return index_common.retrieve_score(scanctx.rmi_hostname, scanctx.job_no, indexctx.rid);

end;

/*
  Ancillary operation for retrieving scores for an extended similarity search.
  Binding for sdf (clob) columns
*/
function similarityScoreFunction (
    sdf clob,
    fingerprint_type varchar2,
    search_method varchar2,
    query varchar2,
    minSimilarity number,
    maxHits integer,
    arg1 number,
    arg2 number,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number as
    hit_list rowIdArray;
    cnt integer;
    v_score number;
begin

    -- get the similarity score from a java cache
    return index_common.retrieve_score(scanctx.rmi_hostname, scanctx.job_no, indexctx.rid);

end;

/*
  Ancillary operation for retrieving scores for full similarity search.
  Binding for rdkit (blob) columns
*/
function similarityScoreFunction (
    mol blob,
    fingerprint_type varchar2,
    search_method varchar2,
    query varchar2,
    minSimilarity number,
    maxHits integer,
    arg1 number,
    arg2 number,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number as
    hit_list rowIdArray;
    cnt integer;
    v_score number;
begin

    -- get the similarity score from a java cache
    return index_common.retrieve_score(scanctx.rmi_hostname, scanctx.job_no, indexctx.rid);

end;

/*
  Common code for all functional extended similarity search functions
*/
function extendedSimilaritySearchFn (
    fingerprint_type varchar2,
    search_method varchar2,
    query varchar2,
    minSimilarity number,
    maxHits integer,
    arg1 number,
    arg2 number,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number as
    cnt integer;
    v_table_name varchar2(30);
    v_owner_name varchar2(30);
    v_column_name varchar2(30);
    v_key_name varchar2(100);
    hit_list rowIdArray;
    similarity_list doubleArray;
    finished boolean;
    is_hit boolean;
    job_no integer;
    rmi_hostname varchar2(1000);
begin

    if scanflg = 1
    then
       -- clean up java cache
       index_common.remove_scores(scanctx.rmi_hostname, scanctx.job_no);
       return 0;
    end if;

    if indexctx is null or indexctx.indexinfo is null
    then
        -- no domain index present
        raise_application_error
                (-20000,
                'C$CSCHEM1-0011 extendedSimilaritySearchFn requires domain index');

    end if;

    -- functional search implementation for an indexed table
    -- as far as I know this is never called.

    if scanctx is null
    then

        -- this is the first call to the function.

        -- first do similarity search
        dbms_output.put_line
            ('doing functional implementation for similarity search');
        v_key_name := index_common.schema_names
            (indexctx.indexinfo, v_owner_name, v_table_name, v_column_name);
        rmi_hostname := index_common.get_rmi_hostname(structure_ind_obj.index_type_name, v_key_name);

        hit_list := rowIdArray();
        extendedSimilarityIndexSearch
            (rmi_hostname, finished, true, job_no, v_owner_name, v_table_name, v_column_name,
             fingerprint_type, search_method, query, minSimilarity,
             hit_list, similarity_list, maxHits, arg1, arg2);

        -- save hitlist in scan context
        scanctx := structure_ind_obj(indexctx.indexinfo, index_common.boolean_to_integer(finished),
                                    job_no, hit_list, similarity_list);
        -- get all remaining chunks
        index_base_obj.fetch_all_chunks(scanctx);

    end if;

    -- determine if the current row is a hit by checking java cache map
    is_hit := index_common.is_hit(scanctx.rmi_hostname, scanctx.job_no, indexctx.rid);
    return index_common.boolean_to_integer(is_hit);

end extendedSimilaritySearchFn;


/*
  Functional operator for extended similarity search. Varchar2 (smiles) column.
*/
function extendedSimilaritySearchFn (
    smiles varchar2,
    fingerprint_type varchar2,
    search_method varchar2,
    query varchar2,
    minSimilarity number,
    maxHits integer,
    arg1 number,
    arg2 number,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number as
begin
  return extendedSimilaritySearchFn(smiles, fingerprint_type, query, minsimilarity,
                 maxhits, arg1, arg2, indexctx, scanctx, scanflg);
end extendedSimilaritySearchFn;


/*
  Functional operator for extended similarity search. Sdf (clob) column.
*/
function extendedSimilaritySearchFn (
    sdf clob,
    fingerprint_type varchar2,
    search_method varchar2,
    query varchar2,
    minSimilarity number,
    maxHits integer,
    arg1 number,
    arg2 number,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number as
begin
  return extendedSimilaritySearchFn(fingerprint_type, search_method, query, minsimilarity,
                 maxhits, arg1, arg2, indexctx, scanctx, scanflg);
end extendedSimilaritySearchFn;

/*
  Functional operator for extended similarity search. RDKit (blob) column.
*/
function extendedSimilaritySearchFn (
    oeMol blob,
    fingerprint_type varchar2,
    search_method varchar2,
    query varchar2,
    minSimilarity number,
    maxHits integer,
    arg1 number,
    arg2 number,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number as
begin
  return extendedSimilaritySearchFn(fingerprint_type, search_method, query, minsimilarity,
                 maxhits, arg1, arg2, indexctx, scanctx, scanflg);
end extendedSimilaritySearchFn;



/*
    Wrapper for Substructure search on RMI indexes.
*/
procedure indexSubstructureSearch (
        rmi_hostname varchar2,
        finished out boolean,
        add_to_map boolean,
        job_no out number,
        owner_name varchar2,
        table_name varchar2,
        column_name varchar2,
        query varchar2,
        hits out nocopy rowIdArray,
        max_hits number,
        use_fingerprint boolean,
        query_type varchar2)
as language Java name
'com.cairn.rmi.oracle.Wrappers.tableIndexSubstructureSearch(
      java.lang.String, boolean[], boolean, int[], java.lang.String,
			java.lang.String, java.lang.String, java.lang.String,
                        java.sql.Array[], int, boolean, java.lang.String)';

/*
    Wrapper for exact match search on RMI indexes.
*/
procedure indexExactMatchSearch (
        rmi_hostname varchar2,
        finished out boolean,
        add_to_map boolean,
        job_no out number,
        owner_name varchar2,
        table_name varchar2,
        column_name varchar2,
        query varchar2,
        hits out nocopy rowIdArray,
        max_hits number)
as language Java name
'com.cairn.rmi.oracle.Wrappers.tableIndexExactMatchSearch(
      java.lang.String, boolean[], boolean, int[], java.lang.String,
			java.lang.String, java.lang.String, java.lang.String,
      java.sql.Array[], int)';

/*
    Wrapper for Java procedure that performs similarity search.
*/
procedure indexSimilaritySearch (
        rmi_hostname varchar2,
        finished out boolean,
        similarity_ancillary in boolean,
        job_no out number,
        owner_name varchar2,
        table_name varchar2,
        column_name varchar2,
        query varchar2,
        min_similarity number,
        hits out nocopy rowIdArray,
        similarity out nocopy doubleArray,
        max_hits number)
as language Java name
'com.cairn.rmi.oracle.Wrappers.tableIndexSimilaritySearch(
      java.lang.String, boolean[], boolean, int[], java.lang.String,
      java.lang.String, java.lang.String, java.lang.String,
      double, java.sql.Array[], java.sql.Array[], int)';

/*
    Wrapper for Substructure search on RMI indexes with clob query
*/
procedure indexClobSubstructureSearch (
        rmi_hostname varchar2,
        finished out boolean,
        add_to_map boolean,
        job_no out number,
        owner_name varchar2,
        table_name varchar2,
        column_name varchar2,
        query clob,
        hits out nocopy rowIdArray,
        max_hits number,
        use_fingerprint boolean,
        query_type varchar2)
as language Java name
'com.cairn.rmi.oracle.Wrappers.tableIndexClobSubstructureSearch
      (java.lang.String, boolean[], boolean, int[], java.lang.String,
       java.lang.String, java.lang.String, oracle.sql.CLOB,
       java.sql.Array[], int, boolean, java.lang.String)';

/*
   Java proceedure to perform extended similarity search on an external index.
 */

procedure extendedSimilarityIndexSearch (
        rmi_hostname varchar2,
        finished out boolean,
        similarity_ancillary in boolean,
        job_no out number,
        owner_name varchar2,
        table_name varchar2,
        column_name varchar2,
        fingerprint_type varchar2,
        search_method varchar2,
        query varchar2,
        min_similarity number,
        hits out nocopy rowIdArray,
        similarity out nocopy doubleArray,
        max_hits number,
        arg1 number,
        arg2 number)
as language Java name
'com.cairn.rmi.oracle.Wrappers.extendedTableIndexSimilaritySearch(
      java.lang.String, boolean[], boolean, int[],
      java.lang.String, java.lang.String, java.lang.String,
      java.lang.String, java.lang.String, java.lang.String,
      double, java.sql.Array[], java.sql.Array[], int,
      java.lang.Double, java.lang.Double)';


end;
/

create or replace type body index_base_obj as

/*
  Copy data from a chunk returned by the RMI server to an Oracle hitlist.
*/
final member procedure copy_chunk_to_rowids (
    self in out nocopy index_base_obj,
    rids in out nocopy SYS.ODCIRIDLIST,
    n_required in out integer,
    rids_position in out integer,
    n_added out integer,
    read_hitlist out boolean,
    wrote_all_rids out boolean)
    As
    hitlist_available integer;
    str varchar2(5);

    begin

    hitlist_available := hitlist.COUNT - hitlist_position;
    if hitlist_available = n_required then
       n_added := hitlist_available;
       read_hitlist := true;
       wrote_all_rids := true;
    elsif hitlist_available > n_required then
       n_added := n_required;
       read_hitlist := false;
       wrote_all_rids := true;
    elsif hitlist_available < n_required then
       n_added := hitlist_available;
       read_hitlist := true;
       wrote_all_rids := false;
    end if;

    -- dbms_output.put_line('copy_chunk_to_rids n_added '||n_added);
    if read_hitlist then
        str := 'true';
    else
        str := 'false';
    end if;
    -- dbms_output.put_line('copy_chunk_to_rids read_hitlist '||str);
    if wrote_all_rids then
        str := 'true';
    else
        str := 'false';
    end if;
    -- dbms_output.put_line('copy_chunk_to_rids wrote_all_ids '||str);

    rids.extend(n_added);
    for i in 1..n_added loop
      rids(rids_position+i) := hitlist(hitlist_position+i);
    end loop;

    rids_position := rids_position + n_added;
    hitlist_position := hitlist_position + n_added;
    n_required := n_required - n_added;


    --dbms_output.put_line('>>>>copy_chunk_to_rids finished chunk copy');

    end;

/*
  Adds new hits to the similarity score table
*/
final member procedure append_scores_to_table(
    self in out nocopy index_base_obj)
as
    strt number;
    i integer;
    idx number;
begin

    -- the similarity score table is now now longer normally used.  The Java
    -- caching system is preferred.

    -- however this can be used to create pl/sql tables

    if build_score_table = 1 then

        if scores is null then
            return;
        end if;

        if score_table is null then
            score_table := similarity_hits();
        end if;

        strt := score_table.count;
        score_table.extend(hitlist.count);
        for i in 1..hitlist.count loop
             idx := i+strt;
             if scores is null then
                 score_table(idx) := similarity_hit(hitlist(i), null);
             else
                 score_table(idx) := similarity_hit(hitlist(i), scores(i));
             --    dbms_output.put_line('Added score '||scores(i)||' for rowid '||
             --      hitlist(i) || ' index '|| idx);
             end if;
        end loop;

    end if;

    if build_hitlist_table = 1 then

        if hitlist is null then
            return;
        end if;

        if hitlist_table is null then
             hitlist_table := hit_table();
        end if;
        strt := hitlist_table.count;

        hitlist_table.extend(hitlist.count);
        for i in 1..hitlist.count loop
            idx := i+strt;
            --dbms_output.put_line('Added hit for rowid '||
            --                    hitlist(i) || ' index '|| idx);
            hitlist_table(idx) := hit_row(hitlist(i));
        end loop;


    end if;

    null;
end;

/*
  Fetches all chunks from the rmi server. The hits from the search are stored
  in the Java map cache and can be retrieved using wrapper utilities.
  This proceedure is designed for the functional interface which needs to scoop up
  all hits.
*/
final member procedure fetch_all_chunks (
    self in out nocopy index_base_obj)
as
    finished boolean := false;
begin

    if self.finished = 1 then
         return;
    end if;

    while finished = false loop
        index_common.retrieve_chunk(self.rmi_hostname, self.job_no, finished, true,
                                    self.hitlist, self.scores);
        append_scores_to_table;
    end loop;
    self.finished := 1;
end;

/*
  Fetches batches of hits from the rmi servers and inserts them into the rids array.

  Should be called from OCDIIndexFetch.  Assumes that the search operator has
  already retrieved the first batch of hits.
*/
final member function fetch_row_ids (
    self in out nocopy index_base_obj,
    nrows in number,
    rids in out nocopy SYS.ODCIRIDLIST)
return number
as
        n_required integer := nrows;
        rids_position integer := 0;
        read_hitlist boolean := false;
        wrote_all_ids boolean := false;
        n_added integer := 0;
        test boolean := false;
        similarity_ancillary boolean := false;
        -- set this true to return chunks as soon as we get them.  We still have to account for chunks that
        -- may be larger than the rids buffer.
        return_on_chunk_read boolean := true;
begin

        if self.similarity_ancillary = 1 then
            similarity_ancillary := true;
        end if;

        if hitList is null and self.finished = 1 then
          dbms_output.put_line('No more results');
          self.hitlist_position := 0;
          rids := null;
          return odciconst.success;
        end if;

        if hitList is null then
            index_common.retrieve_chunk(self.rmi_hostname, self.job_no, test,
                                        similarity_ancillary, self.hitlist, self.scores);
            append_scores_to_table;

            dbms_output.put_line('Got another chunk of length '||self.hitlist.count);
            self.finished := index_common.boolean_to_integer(test);
        end if;

        rids := SYS.ODCIRIDLIST();
        loop
          index_base_obj.copy_chunk_to_rowids(self, rids, n_required, rids_position,
                 n_added, read_hitlist, wrote_all_ids);
          -- dbms_output.put_line('Copied '||n_added||' values to rowids');
          if read_hitlist then
            if self.finished = 1 then
                dbms_output.put_line('Got all results current batch size '||rids.count);
                self.hitlist_position := 0;
                self.hitlist := null;
                return odciconst.success;
            else
                -- read a complete chunk
                if return_on_chunk_read then
                     -- got a complete chunk return this to Oracle immediately, read the next chunk when this routine is next called
                     wrote_all_ids := true;
                     self.hitlist_position := 0;
                     self.hitList := null;
                elsif not wrote_all_ids then
                     -- get the next chunk and carry on filling the rids array
                     index_common.retrieve_chunk(self.rmi_hostname, self.job_no, test,
                                            similarity_ancillary, self.hitlist, self.scores);
                     append_scores_to_table;

                     dbms_output.put_line('Got another chunk of length '||self.hitlist.count);
                     self.finished := index_common.boolean_to_integer(test);
                 elsif wrote_all_ids then
                     -- get the next chunk when this routine is next called (chunk size matches Oracle rids size
                     self.hitlist_position := 0;
                     self.hitList := null;
                 end if;
            end if;
          end if;

          if wrote_all_ids then
                dbms_output.put_line('fetch_row_ids: got another batch size '|| rids.count);
                return odciconst.success;
          end if;

        end loop;

end;


end;
/

create or replace package body index_common as

/*
    Gets the next set of task results from the RMI server.  Can also fetch overlays.
*/
  procedure retrieve_chunk (
    rmi_hostname varchar2,
    job_no number,
    finished out boolean,
    similarity_ancillary in boolean,
    hitlist out nocopy rowIdArray,
    scores out nocopy doubleArray)
as language Java name
'com.cairn.rmi.oracle.Wrappers.retrieveChunk(
      java.lang.String, int, boolean[], boolean,
      java.sql.Array[], java.sql.Array[])';
/*
    Gets a score for a search job and rowid from Java
*/
  function retrieve_score (
    rmi_hostname varchar2,
    job_no number,
    rowid varchar2) return number
as language java name
'com.cairn.rmi.oracle.Wrappers.getSimilarityScore(
      java.lang.String, int, java.lang.String) return java.lang.Double';

/*
   Removes a cache of scores from Java
*/
  procedure remove_scores (
    rmi_hostname varchar2,
    job_no number)
as language java name
'com.cairn.rmi.oracle.Wrappers.removeScoresMap(
      java.lang.String, int)';

/*
   Determines if a row is a hit in a given search job by checking the Java cache.
*/
function is_hit (
    rmi_hostname varchar2,
    job_no number,
    rowid varchar2) return boolean
as language java name
'com.cairn.rmi.oracle.Wrappers.isHit(
      java.lang.String, int, java.lang.String) return boolean';

/*
   Converts a boolean to integer (true is 1 and false is 0)
*/
  function boolean_to_integer (
      val boolean) return integer
as
begin
    if val = true then
        return 1;
    elsif val is null then
        return null;
    else
        return 0;
    end if;
end;

/*
    Converts an integer to boolean (zero is false)
*/
  function integer_to_boolean (
      val integer) return boolean
as
begin
    if val = 0 then
        return false;
    elsif val is null then
        return null;
    else
        return true;
    end if;
end;


/*
    Gets schema name of the column being indexed from ODCIINDEXINFO.
    We use this as a key to reference the index.
*/
function key_name(
    ia SYS.ODCIINDEXINFO)
return varchar2
as
    v_table_name varchar2(30);
    v_owner_name varchar2(30);
    v_column_name varchar2(30);
    v_key_name varchar2(100);
begin

    v_owner_name := ia.INDEXCOLS(1).TABLESCHEMA;
    v_table_name := ia.INDEXCOLS(1).TABLENAME;
    v_column_name := replace(ia.INDEXCOLS(1).COLNAME, '"');

    v_key_name := v_owner_name || '.' || v_table_name || '.' || v_column_name;
    -- dbms_output.put_line('key_name is '||v_key_name);
    return v_key_name;

end;

/*
    Extracts owner table and column names from ODCIINDEXINFO.
    Returns key name.
*/
function schema_names(
    ia SYS.ODCIINDEXINFO,
    v_owner_name out nocopy varchar2,
    v_table_name out nocopy varchar2,
    v_column_name out nocopy varchar2)
return varchar2
as
    v_key_name varchar2(100);
begin

    if ia is null
    then
        raise_application_error
                (-20000,
                'C$CSCHEM1-0021 schemaNames requires domain index');
        return 0;
    end if;

    v_owner_name := ia.INDEXCOLS(1).TABLESCHEMA;
    v_table_name := ia.INDEXCOLS(1).TABLENAME;
    v_column_name := replace(ia.INDEXCOLS(1).COLNAME, '"');

    v_key_name := v_owner_name || '.' || v_table_name || '.' || v_column_name;
    -- dbms_output.put_line('schemaNames is '||v_key_name);
    return v_key_name;
end;


function params_to_rmi_hostname(
    params varchar2) return VARCHAR2
as
    rmi_hostname varchar2(1000);
begin
    rmi_hostname := substr(regexp_substr(params, 'rmi_hostname:[[:alnum:][:punct:]]+' ), 14);
    if rmi_hostname is not null then
         dbms_output.put_line('params_to_rmi_hostname: rmi_hostname is '||rmi_hostname);
    else
         select rmi_hostname into rmi_hostname from rmi_hostname;
    end if;
    return rmi_hostname;
end;

/*
    Creates the change log table and puts an entry in the index_lookup
    table so we can find it again.
*/
function create_lookup_and_change_table(
    index_type_name varchar2,
    owner_name varchar2,
    table_name varchar2,
    column_name varchar2,
    key_name varchar2,
    v_indexname varchar2,
    rmi_hostname varchar2)
return varchar2
as
    v_index_lookup_id number;
    v_change_table_name varchar2(30);
    ddl_str varchar2(1000);
    index_column_type varchar2(106);
begin

    begin
        select index_lookup_id into v_index_lookup_id
          from index_lookup
         where index_key = key_name and index_type = index_type_name;
        -- if we're here then lookup entry already exists
        raise_application_error
            (-20000, 'C$CSCHEM1-0002 change log entry already exists');
    exception
        when no_data_found then
            null;
    end;

    v_index_lookup_id := index_lookup_id_seq.nextVal;
    v_change_table_name := 'change_log_'||v_index_lookup_id;

    insert into index_lookup (index_lookup_id, index_key, change_table_name, indexname, index_type, rmi_hostname)
           values (v_index_lookup_id, key_name, v_change_table_name, v_indexname, index_type_name, rmi_hostname);

    index_column_type := get_index_column_type(owner_name, table_name, column_name);
    if index_column_type = 'VARCHAR2' then
        index_column_type := 'VARCHAR2(4000)';
    end if;

    ddl_str := 'create table c$cschem1.'|| v_change_table_name ||
                '(row_change_id  number primary key,
                  row_changed rowid,
                  new_value '|| index_column_type ||',
                  old_value '|| index_column_type ||')';


    --dbms_output.put_line('createChangeLogTable: executing '||ddl_str);
    execute immediate ddl_str;
    dbms_output.put_line('created log table c$cschem1.'||v_change_table_name ||' for '||
                          key_name);

    return v_change_table_name;
end;

/*
    Gets the name of the change log table from the index_lookup.
*/
function get_change_log_table_name (
    index_type_name varchar2,
    key_name varchar2)
return varchar2
as
    v_change_table_name varchar2(30);
begin
    select change_table_name into v_change_table_name
      from index_lookup where index_key = key_name and index_type = index_type_name;

    return v_change_table_name;
end;

PROCEDURE get_index_settings (
    index_info sys.ODCIINDEXINFO,
    index_type_name varchar2,
    rmi_hostname out nocopy varchar2,
    key_name out nocopy varchar2,
    index_lookup_id out integer)
as
    v_owner_name varchar2(30);
    v_table_name varchar2(30);
    v_column_name varchar2(30);
begin
    v_owner_name := index_info.INDEXCOLS(1).TABLESCHEMA;
    v_table_name := index_info.INDEXCOLS(1).TABLENAME;
    v_column_name := replace(index_info.INDEXCOLS(1).COLNAME, '"');

    key_name := v_owner_name || '.' || v_table_name || '.' || v_column_name;

    select rmi_hostname, index_lookup_id into rmi_hostname, index_lookup_id from index_lookup
     where index_key = key_name and index_type = index_type_name;

    if rmi_hostname is null
    then
        select rmi_hostname into rmi_hostname from rmi_hostname;
    end if;
end;

/*
   Determines the rmi server for the index.  This can be stored in the index
   lookup- otherwise get it from the rmi_hostname table.
*/
function get_rmi_hostname (
    index_type_name varchar2,
    owner_name varchar2,
    table_name varchar2,
    column_name varchar2)
    return varchar2
as
    key_name varchar2(1000);
begin
    key_name := upper(owner_name || '.' || table_name || '.' || column_name);
    return get_rmi_hostname (index_type_name, key_name);
end;

/*
    Determines the rmi server for the index.  This can be stored in the index
    lookup- otherwise get it from the rmi_hostname table.
*/
function get_rmi_hostname (
    index_type_name varchar2,
    key_name varchar2)
    return varchar2
as
    v_rmi_hostname varchar2(500);
begin

    begin
        select rmi_hostname  into v_rmi_hostname from index_lookup
         where index_key = key_name and index_type = index_type_name;
    exception
        when no_data_found then
            null;
    end;

    if v_rmi_hostname is null
    then
        select rmi_hostname into v_rmi_hostname from rmi_hostname;
    end if;

    return v_rmi_hostname;
end;

/*
    Determines the column type for the index.
    Should be one of varchar2 (smiles), clob (sdf) or blob (OEMol).
*/
function get_index_column_type (
    v_owner_name varchar2,
    v_table_name varchar2,
    v_column_name varchar2)
    return varchar2
as
   v_index_column_type varchar2(106);
begin
   select data_type into v_index_column_type from all_tab_columns
    where owner = v_owner_name and table_name = v_table_name and column_name = v_column_name;
   return v_index_column_type;
end;

/*
    Truncates the change log table.
*/
procedure truncate_change_log_table(
    index_type_name varchar2,
    key_name varchar2)
as
    v_change_table_name varchar2(30);
    ddl_str varchar2(1000);
begin
   v_change_table_name := get_change_log_table_name(index_type_name, key_name);
   ddl_str := 'truncate table c$cschem1.'||v_change_table_name;
   execute immediate ddl_str;
   dbms_output.put_line('truncated log table c$cschem1.'||v_change_table_name ||' for '||
                          key_name);
end;

/*
    Drops the change log table and removes the entry from lookup index.
*/
procedure drop_change_log_table(
    index_type_name varchar2,
    key_name varchar2)
as
    v_change_table_name varchar2(30);
    ddl_str varchar2(1000);
begin
   v_change_table_name := get_change_log_table_name(index_type_name, key_name);
   dbms_output.put_line('about delete index key '|| key_name || ' type ' || index_type_name || ' from lookup');
   delete from index_lookup where index_key = key_name and index_type = index_type_name;
   ddl_str := 'drop table c$cschem1.'||v_change_table_name;
   dbms_output.put_line('about to drop log table c$cschem1.'||v_change_table_name ||' for '||
                         key_name);
   execute immediate ddl_str;
   dbms_output.put_line('dropped log table c$cschem1.'||v_change_table_name ||' for '||
                          key_name);
end;

/*
   Renames an index
*/
  procedure rename_index(
      index_name varchar2,
      index_type_name varchar2,
      key_name varchar2)
as
begin
    -- when the index is renamed we simply have to update the index_lookup table
    update index_lookup set indexname = index_name
     where index_key = key_name and index_type = index_type_name;
    dbms_output.put_line('Renamed index '|| key_name||' to '|| index_name);
end;

/*
    A row has been edited in the index column.
    Put it in the change log table.  For varchar2 columns.
*/
function index_update(
      index_type_name varchar2,
      index_info sys.odciindexinfo,
      rid rowid,
      oldval varchar2,
      newval varchar2)
  return number
as
        insert_dml varchar2(1000);
        key_name varchar2(100);
        v_change_table_name varchar2(30);
begin
        key_name := index_common.key_name(index_info);
        v_change_table_name := get_change_log_table_name(index_type_name, key_name);

        insert_dml := 'insert into c$cschem1.'||v_change_table_name ||
            '(row_change_id, row_changed, new_value, old_value) '||
            'values (:1, :2, :3, :4)';
        execute immediate insert_dml
                using row_change_id_seq.nextVal, rid, newval, oldval;

        RETURN odciconst.success;
end;

/*
    A row has been edited in the index column.
    Put it in the change log table.  For clob columns.
*/
function index_update(
      index_type_name varchar2,
      index_info sys.odciindexinfo,
      rid rowid,
      oldval clob,
      newval clob)
  return number
as
        insert_dml varchar2(1000);
        key_name varchar2(100);
        v_change_table_name varchar2(30);
begin
        key_name := index_common.key_name(index_info);
        v_change_table_name := get_change_log_table_name(index_type_name, key_name);

        insert_dml := 'insert into c$cschem1.'||v_change_table_name ||
            '(row_change_id, row_changed, new_value, old_value) '||
            'values (:1, :2, :3, :4)';
        execute immediate insert_dml
                using row_change_id_seq.nextVal, rid, newval, oldval;

        RETURN odciconst.success;
end;

/*
    A row has been edited in the index column.
    Put it in the change log table.  For blob columns.
*/
function index_update(
      index_type_name varchar2,
      index_info sys.odciindexinfo,
      rid rowid,
      oldval blob,
      newval blob)
  return number
as
        insert_dml varchar2(1000);
        key_name varchar2(100);
        v_change_table_name varchar2(30);
begin
        key_name := index_common.key_name(index_info);
        v_change_table_name := get_change_log_table_name(index_type_name, key_name);

        insert_dml := 'insert into c$cschem1.'||v_change_table_name ||
            '(row_change_id, row_changed, new_value, old_value) '||
            'values (:1, :2, :3, :4)';
        execute immediate insert_dml
                using row_change_id_seq.nextVal, rid, newval, oldval;

        RETURN odciconst.success;
end;

/*
    A row has been deleted in the index column.
    Put it in the change log table. Varchar2 version
*/
  function index_delete(
      index_type_name varchar2,
      index_info sys.odciindexinfo,
      rid rowid,
      oldval varchar2)
  return number
as
        insert_dml varchar2(1000);
        key_name varchar2(100);
        v_change_table_name varchar2(30);
begin
        key_name := index_common.key_name(index_info);
        v_change_table_name := index_common.get_change_log_table_name
                                    (index_type_name, key_name);

        insert_dml := 'insert into c$cschem1.'||v_change_table_name ||
            '(row_change_id, row_changed, old_value) values (:1, :2, :3)';
        dbms_output.put_line('index_delete running: '||insert_dml);
        execute immediate insert_dml using row_change_id_seq.nextVal, rid, oldval;

        RETURN odciconst.success;
end;

/*
    A row has been deleted in the index column.
    Put it in the change log table. clob version
*/
  function index_delete(
      index_type_name varchar2,
      index_info sys.odciindexinfo,
      rid rowid,
      oldval clob)
  return number
as
        insert_dml varchar2(1000);
        key_name varchar2(100);
        v_change_table_name varchar2(30);
begin
        key_name := index_common.key_name(index_info);
        v_change_table_name := index_common.get_change_log_table_name
                                    (index_type_name, key_name);

        insert_dml := 'insert into c$cschem1.'||v_change_table_name ||
            '(row_change_id, row_changed, old_value) values (:1, :2, :3)';
        dbms_output.put_line('index_delete running: '||insert_dml);
        execute immediate insert_dml using row_change_id_seq.nextVal, rid, oldval;

        RETURN odciconst.success;
end;

/*
    A row has been deleted in the index column.
    Put it in the change log table. Blob version
*/
  function index_delete(
      index_type_name varchar2,
      index_info sys.odciindexinfo,
      rid rowid,
      oldval blob)
  return number
as
        insert_dml varchar2(1000);
        key_name varchar2(100);
        v_change_table_name varchar2(30);
begin
        key_name := index_common.key_name(index_info);
        v_change_table_name := index_common.get_change_log_table_name
                                    (index_type_name, key_name);

        insert_dml := 'insert into c$cschem1.'||v_change_table_name ||
            '(row_change_id, row_changed, old_value) values (:1, :2, :3)';
        dbms_output.put_line('index_delete running: '||insert_dml);
        execute immediate insert_dml using row_change_id_seq.nextVal, rid, oldval;

        RETURN odciconst.success;
end;

/*
    A row has been inserted in the index column.
    Put it in the change log table. Varchar2 version
*/
function index_insert(
      index_type_name varchar2,
      index_info sys.odciindexinfo,
      rid rowid,
      newval varchar2)
  return number
is
        insert_dml varchar2(1000);
        key_name varchar2(100);
        v_change_table_name varchar2(30);
begin
        key_name := index_common.key_name(index_info);
        v_change_table_name := index_common.get_change_log_table_name
                                  (index_type_name, key_name);

        insert_dml := 'insert into c$cschem1.'||v_change_table_name ||
            '(row_change_id, row_changed, new_value) values (:1, :2, :3)';
        execute immediate insert_dml using row_change_id_seq.nextVal, rid, newval;

        RETURN odciconst.success;
end;

/*
    A row has been inserted in the index column.
    Put it in the change log table. Clob version
*/
function index_insert(
      index_type_name varchar2,
      index_info sys.odciindexinfo,
      rid rowid,
      newval clob)
  return number
is
        insert_dml varchar2(1000);
        key_name varchar2(100);
        v_change_table_name varchar2(30);
begin
        key_name := index_common.key_name(index_info);
        v_change_table_name := index_common.get_change_log_table_name
                                  (index_type_name, key_name);

        insert_dml := 'insert into c$cschem1.'||v_change_table_name ||
            '(row_change_id, row_changed, new_value) values (:1, :2, :3)';
        execute immediate insert_dml using row_change_id_seq.nextVal, rid, newval;

        RETURN odciconst.success;
end;

/*
    A row has been inserted in the index column.
    Put it in the change log table. Blob version
*/
function index_insert(
      index_type_name varchar2,
      index_info sys.odciindexinfo,
      rid rowid,
      newval blob)
  return number
is
        insert_dml varchar2(1000);
        key_name varchar2(100);
        v_change_table_name varchar2(30);
begin
        key_name := index_common.key_name(index_info);
        v_change_table_name := index_common.get_change_log_table_name
                                  (index_type_name, key_name);

        insert_dml := 'insert into c$cschem1.'||v_change_table_name ||
            '(row_change_id, row_changed, new_value) values (:1, :2, :3)';
        execute immediate insert_dml using row_change_id_seq.nextVal, rid, newval;

        RETURN odciconst.success;
end;

end index_common;
/

