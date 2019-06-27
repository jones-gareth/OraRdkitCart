create or replace package index_common as

/*
  See package body for comments
*/

  procedure retrieve_chunk (
    rmi_hostname varchar2,
    job_no number,
    finished out boolean,
    similarity_ancillary in boolean,
    hitlist out nocopy rowIdArray,
    scores out nocopy doubleArray);

  function retrieve_score (
     rmi_hostname varchar2, job_no number, rowid varchar2) return number;

  function is_hit (
     rmi_hostname varchar2, job_no number, rowid varchar2) return boolean;

  procedure remove_scores (
     rmi_hostname varchar2, job_no number);

  function boolean_to_integer (val boolean) return integer;

  function integer_to_boolean (val integer) return boolean;

  function key_name(
    ia SYS.ODCIINDEXINFO)
  return varchar2;

  function schema_names(
    ia SYS.ODCIINDEXINFO,
    v_owner_name out nocopy varchar2,
    v_table_name out nocopy varchar2,
    v_column_name out nocopy varchar2)
  return varchar2;

  function params_to_rmi_hostname(
    params varchar2) return VARCHAR2;

  function create_lookup_and_change_table(
    index_type_name varchar2,
    owner_name varchar2,
    table_name varchar2,
    column_name varchar2,
    key_name varchar2,
    v_indexname varchar2,
    rmi_hostname varchar2)
  return varchar2;

  function get_change_log_table_name (
      index_type_name varchar2,
      key_name varchar2)
  return varchar2;

  function get_rmi_hostname (
    index_type_name varchar2,
    owner_name varchar2,
    table_name varchar2,
    column_name varchar2)
    return varchar2;

  function get_index_column_type (
    v_owner_name varchar2,
    v_table_name varchar2,
    v_column_name varchar2)
    return varchar2;

  function get_rmi_hostname (
    index_type_name varchar2,
    key_name varchar2)
    return varchar2;

   PROCEDURE get_index_settings (
    index_info sys.ODCIINDEXINFO,
    index_type_name varchar2,
    rmi_hostname out nocopy varchar2,
    key_name out nocopy varchar2,
    index_lookup_id out integer);

  procedure truncate_change_log_table(
      index_type_name varchar2,
      key_name varchar2);

  procedure drop_change_log_table(
      index_type_name varchar2,
      key_name varchar2);

  procedure rename_index(
      index_name varchar2,
      index_type_name varchar2,
      key_name varchar2);

  function index_update(
      index_type_name varchar2,
      index_info sys.odciindexinfo,
      rid rowid,
      oldval varchar2,
      newval varchar2)
  return number;

  function index_update(
      index_type_name varchar2,
      index_info sys.odciindexinfo,
      rid rowid,
      oldval clob,
      newval clob)
  return number;

  function index_update(
      index_type_name varchar2,
      index_info sys.odciindexinfo,
      rid rowid,
      oldval blob,
      newval blob)
  return number;

  function index_delete(
      index_type_name varchar2,
      index_info sys.odciindexinfo,
      rid rowid,
      oldval varchar2)
  return number;

 function index_delete(
      index_type_name varchar2,
      index_info sys.odciindexinfo,
      rid rowid,
      oldval clob)
  return number;

 function index_delete(
      index_type_name varchar2,
      index_info sys.odciindexinfo,
      rid rowid,
      oldval blob)
  return number;

  function index_insert(
      index_type_name varchar2,
      index_info sys.odciindexinfo,
      rid rowid,
      newval varchar2)
  return number;

  function index_insert(
      index_type_name varchar2,
      index_info sys.odciindexinfo,
      rid rowid,
      newval clob)
  return number;

  function index_insert(
      index_type_name varchar2,
      index_info sys.odciindexinfo,
      rid rowid,
      newval blob)
  return number;

end index_common;
/

create or replace type index_base_obj as object (
    index_lookup_id integer,
    rmi_hostname varchar2(1000),
    index_info SYS.ODCIINDEXINFO,

    -- buffer of rows and scores retrieved from RMI server
    hitlist rowIdArray,
    scores doubleArray,
    -- current position in the buffer
    hitlist_position integer,
    -- pl/sql tables built from hits
    score_table similarity_hits,
    hitlist_table hit_table,
    -- these guys are booleans/flags
    finished integer,
    job_no integer,
    similarity_ancillary integer,
    overlay_ancillary integer,
    build_score_table integer,
    build_hitlist_table integer,

  final member procedure copy_chunk_to_rowids (
    self in out nocopy index_base_obj,
    rids in out nocopy SYS.ODCIRIDLIST,
    n_required in out integer,
    rids_position in out integer,
    n_added out integer,
    read_hitlist out boolean,
    wrote_all_rids out boolean),

  final member procedure append_scores_to_table(
    self in out nocopy index_base_obj),

  final member function fetch_row_ids (
    self in out nocopy index_base_obj,
    nrows in number,
    rids in out nocopy SYS.ODCIRIDLIST)
  return number,

  final member procedure fetch_all_chunks (
    self in out nocopy index_base_obj)

)
not instantiable not final;
/

create or replace TYPE  structure_ind_obj under index_base_obj
(
/*
  See package body for comments
*/

constructor function structure_ind_obj (
        index_info sys.ODCIINDEXINFO,
        finished integer,
        job_no integer,
        hitList rowIdArray)
return self as result,

constructor function structure_ind_obj (
        index_info sys.ODCIINDEXINFO,
        finished integer,
        job_no integer,
        hitlist rowIdArray,
        similarity_list doubleArray)
return self as result,

STATIC FUNCTION ODCIGETINTERFACES(ifclist OUT nocopy SYS.ODCIOBJECTLIST) RETURN NUMBER,

STATIC FUNCTION ODCIINDEXCREATE (
        ia SYS.ODCIINDEXINFO,
        parms VARCHAR2,
        env SYS.ODCIEnv)
RETURN NUMBER,

STATIC FUNCTION ODCIIndexAlter (
    ia sys.ODCIIndexInfo,
    parms IN OUT nocopy VARCHAR2,
    altopt NUMBER,
    env sys.ODCIEnv)
RETURN NUMBER,

STATIC FUNCTION ODCIINDEXTRUNCATE (
        ia SYS.ODCIINDEXINFO,
        env SYS.ODCIEnv)
RETURN NUMBER,

STATIC FUNCTION ODCIINDEXDROP(
        ia SYS.ODCIINDEXINFO,
        env SYS.ODCIEnv)
RETURN NUMBER,

STATIC FUNCTION ODCIINDEXINSERT(
        ia SYS.ODCIINDEXINFO,
        rid ROWID,
        newval varchar2,
        env SYS.ODCIEnv)
RETURN NUMBER,

STATIC FUNCTION ODCIINDEXDELETE(
        ia SYS.ODCIINDEXINFO,
        rid ROWID,
        oldval varchar2,
        env SYS.ODCIEnv)
RETURN NUMBER,

STATIC FUNCTION ODCIINDEXUPDATE(
        ia SYS.ODCIINDEXINFO,
        rid ROWID,
        oldval varchar2,
        newval varchar2,
        env SYS.ODCIEnv)
RETURN NUMBER,

STATIC FUNCTION ODCIINDEXINSERT(
        ia SYS.ODCIINDEXINFO,
        rid ROWID,
        newval clob,
        env SYS.ODCIEnv)
RETURN NUMBER,

STATIC FUNCTION ODCIINDEXDELETE(
        ia SYS.ODCIINDEXINFO,
        rid ROWID,
        oldval clob,
        env SYS.ODCIEnv)
RETURN NUMBER,

STATIC FUNCTION ODCIINDEXUPDATE(
        ia SYS.ODCIINDEXINFO,
        rid ROWID,
        oldval clob,
        newval clob,
        env SYS.ODCIEnv)
RETURN NUMBER,

STATIC FUNCTION ODCIINDEXINSERT(
        ia SYS.ODCIINDEXINFO,
        rid ROWID,
        newval blob,
        env SYS.ODCIEnv)
RETURN NUMBER,

STATIC FUNCTION ODCIINDEXDELETE(
        ia SYS.ODCIINDEXINFO,
        rid ROWID,
        oldval blob,
        env SYS.ODCIEnv)
RETURN NUMBER,

STATIC FUNCTION ODCIINDEXUPDATE(
        ia SYS.ODCIINDEXINFO,
        rid ROWID,
        oldval blob,
        newval blob,
        env SYS.ODCIEnv)
RETURN NUMBER,

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
RETURN NUMBER,

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
RETURN NUMBER,

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
return number,

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
return number,

MEMBER FUNCTION ODCIINDEXFETCH(
        SELF IN OUT nocopy structure_ind_obj,
        nrows NUMBER,
        rids OUT nocopy SYS.ODCIRIDLIST,
        env SYS.ODCIEnv)
RETURN NUMBER,

MEMBER FUNCTION ODCIINDEXCLOSE(
    SELF IN OUT nocopy structure_ind_obj,
    env SYS.ODCIEnv)
RETURN NUMBER,

static function index_type_name
return varchar2
);
/

create or replace PACKAGE  INDEX_UTL AS

/*
  See package body for comments
*/


function tableIndexOperation (
        rmi_hostname varchar2,
        owner_name varchar2,
        table_name varchar2,
        column_name varchar2,
        operation varchar2)
return boolean;


function tableIndexOperation (
        rmi_hostname varchar2,
        owner_name varchar2,
        table_name varchar2,
        column_name varchar2,
        operation varchar2,
        params varchar2)
return boolean;


function substructureSearchFunction (
    smiles varchar2,
    query varchar2,
    maxHits integer,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number;

function mdlSubstructureSearchFunction (
    smiles varchar2,
    query varchar2,
    maxHits integer,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number;

function exactMatchSearchFunction (
    smiles varchar2,
    query varchar2,
    maxHits integer,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number;

function similaritySearchFunction (
    smiles varchar2,
    query varchar2,
    minSimilarity number,
    maxHits integer,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number;

function similarityScoreFunction (
    smiles varchar2,
    query varchar2,
    minSimilarity number,
    maxHits integer,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number;


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
return number;

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
return number;

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
return number;

function mdlSubstructClobSearchFunction (
    smiles varchar2,
    query clob,
    maxHits integer,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number;

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
        query_type varchar2);

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
        max_hits number);

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
        max_hits number);

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
        query_type varchar2);
        
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
        arg2 number);

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
return number;

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
return number;

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
return number;

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
return number;

function similaritySearchFunction (
    sdf clob,
    query varchar2,
    minSimilarity number,
    maxHits integer,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number;

function exactMatchSearchFunction (
    sdf clob,
    query varchar2,
    maxHits integer,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number;

function exactMatchSearchFunction (
    oemol blob,
    query varchar2,
    maxHits integer,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number;

function similaritySearchFunction (
    oemol blob,
    query varchar2,
    minSimilarity number,
    maxHits integer,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number;

function substructureSearchFunction (
    sdf clob,
    query varchar2,
    maxHits integer,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number;

function substructureSearchFunction (
    oemol blob,
    query varchar2,
    maxHits integer,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number;

function mdlSubstructClobSearchFunction (
    sdf clob,
    query clob,
    maxHits integer,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number;

function mdlSubstructClobSearchFunction (
    oemol blob,
    query clob,
    maxHits integer,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number;

function mdlSubstructureSearchFunction (
    sdf clob,
    query varchar2,
    maxHits integer,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number;

function mdlSubstructureSearchFunction (
    oemol blob,
    query varchar2,
    maxHits integer,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number;

function similarityScoreFunction (
    sdf clob,
    query varchar2,
    minSimilarity number,
    maxHits integer,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number;

function similarityScoreFunction (
    oeMol blob,
    query varchar2,
    minSimilarity number,
    maxHits integer,
    indexctx in sys.ODCIINDEXCTX,
    scanctx in out nocopy structure_ind_obj,
    scanflg in number)
return number;

END;
/

create or replace package chem_structure as

/*
    See package body for comments.
*/

type structure_cursor is ref cursor;


function molecularWeight(
	smiles varchar2)
return number;

function molecularWeight(
        rmi_hostname varchar2,
	smiles varchar2)
return number;

function exactMass(
	smiles varchar2)
return number;

function exactMass(
        rmi_hostname varchar2,
	smiles varchar2)
return number;


function translateStructure(
	inputMolecule varchar2,
	fromFormat varchar2,
	toFormat varchar2)
return varchar2;

function getFingerprint(
	query boolean,
	inputMolecule varchar2)
return varchar2;

function sqlGetFingerprint(
	strQuery char,
	inputMolecule varchar2)
return varchar2;

function tanimotoSimilarity(
	structure1 varchar2,
  structure2 varchar2)
return number;

function canonicalizeSmiles (
     smiles in varchar2)
return varchar2;

function canonicalizeSmiles (
    rmi_hostname varchar2,
    smiles in varchar2)
return varchar2;

procedure sqlQueryStructuresToCache (
        smiles_query in varchar2);

procedure queryStructuresToCache (
	structure_csr in structure_cursor);

function tableIndexSubstructureSearch (
        owner_name varchar2,
        table_name varchar2,
        column_name varchar2,
        query varchar2,
        max_hits integer := -1,
        use_fingerprint char := 'Y',
        query_type varchar2 := 'smarts')
return hit_table;


function tableIndexSimilaritySearch (
        owner_name varchar2,
        table_name varchar2,
        column_name varchar2,
        query varchar2,
        min_similarity number,
        max_hits integer := -1)
return similarity_hits;

function tableIndexExactMatchSearch (
        owner_name varchar2,
        table_name varchar2,
        column_name varchar2,
        query varchar2,
        max_hits integer := -1)
return hit_table;

function sqlTableIndexOperation (
        owner_name varchar2,
        table_name varchar2,
        column_name varchar2,
        operation varchar2)
return char;

function tableIndexSubstructSqlFilter (
        owner_name varchar2,
        table_name varchar2,
        column_name varchar2,
        sql_filter varchar2,
        query varchar2,
        max_hits integer := -1,
        bind_params paramArray := paramArray(),
        query_type varchar2 := 'smarts')
return hit_table;

function tableIndexSimilaritySqlFilter (
        owner_name varchar2,
        table_name varchar2,
        column_name varchar2,
        sql_filter varchar2,
        query varchar2,
        min_similarity number,
        max_hits integer := -1,
        bind_params paramArray := paramArray())
return similarity_hits;


procedure tableIndexExtractSmiles (
    owner_name varchar2,
    table_name varchar2,
    column_name varchar2,
    sql_query varchar2,
    sql_update varchar2,
    sql_user varchar2,
    sql_password varchar2);

function tableIndexGetRowSmiles (
    owner_name varchar2,
    table_name varchar2,
    column_name varchar2,
    row_id varchar2)
return varchar2;

function tableIndexGetIdSmiles (
    owner_name varchar2,
    table_name varchar2,
    column_name varchar2,
    id_column_name varchar2,
    table_id varchar2)
return varchar2;

function tableIndexGetRowFingerprint (
    owner_name varchar2,
    table_name varchar2,
    column_name varchar2,
    row_id varchar2)
return varchar2;

function tableIndexGetIdFingerprint (
    owner_name varchar2,
    table_name varchar2,
    column_name varchar2,
    id_column_name varchar2,
    table_id varchar2)
return varchar2;

function tableIndexGetRowSimilarity (
    owner_name varchar2,
    table_name varchar2,
    column_name varchar2,
    row_id1 varchar2,
    row_id2 varchar2)
return number;

function tableIndexGetIdSimilarity (
    owner_name varchar2,
    table_name varchar2,
    column_name varchar2,
    id_column_name varchar2,
    table_id1 varchar2,
    table_id2 varchar2)
return number;


function funcExactMatchSearch (
        target varchar2,
        query varchar2)
return boolean;

function funcClobSubstructureSearch (
        target varchar2,
        query clob,
        query_type varchar2)
return boolean;

function funcSubstructureSearch (
        target varchar2,
        query varchar2,
        query_type varchar2)
return boolean;

function funcSimilaritySearch (
        target varchar2,
        query varchar2,
        minSimilarity number)
return boolean;

procedure enableCache;
    
procedure disableCache;

end;
/

