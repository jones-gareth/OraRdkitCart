
-- recompile to make sure all dependencies are resolved

alter package index_common compile package;
alter type index_base_obj compile;
alter type structure_ind_obj compile;
alter package index_utl compile package;
alter package chem_structure compile package;

-- create 2D index operators and index type

-- substructure search using smarts pattern, or mdl clob pattern
create or replace operator substructure
binding 
(varchar2, varchar2, integer) return number
with index context, scan context structure_ind_obj
using index_utl.substructureSearchFunction,
(clob, varchar2, integer) return number
with index context, scan context structure_ind_obj
using index_utl.substructureSearchFunction,
(blob, varchar2, integer) return number
with index context, scan context structure_ind_obj
using index_utl.substructureSearchFunction,
(varchar2, clob, integer) return number
with index context, scan context structure_ind_obj
using index_utl.mdlSubstructClobSearchFunction,
(clob, clob, integer) return number
with index context, scan context structure_ind_obj
using index_utl.mdlSubstructClobSearchFunction,
(blob, clob, integer) return number
with index context, scan context structure_ind_obj
using index_utl.mdlSubstructClobSearchFunction;

-- substructure search using mdl query with varchar2 or clob binding
create or replace operator mdl_substructure
binding 
(varchar2, varchar2, integer) return number
with index context, scan context structure_ind_obj
using index_utl.mdlSubstructureSearchFunction,
(clob, varchar2, integer) return number
with index context, scan context structure_ind_obj
using index_utl.mdlSubstructureSearchFunction,
(blob, varchar2, integer) return number
with index context, scan context structure_ind_obj
using index_utl.mdlSubstructureSearchFunction,
(varchar2, clob, integer) return number
with index context, scan context structure_ind_obj
using index_utl.mdlSubstructClobSearchFunction,
(clob, clob, integer) return number
with index context, scan context structure_ind_obj
using index_utl.mdlSubstructClobSearchFunction,
(blob, clob, integer) return number
with index context, scan context structure_ind_obj
using index_utl.mdlSubstructClobSearchFunction;

-- substructure search using mdl query with clob binding, keep this
--  form for compatability with earlier versions.
create or replace operator mdl_clob_substructure
binding 
(varchar2, clob, integer) return number
with index context, scan context structure_ind_obj
using index_utl.mdlSubstructClobSearchFunction,
(clob, clob, integer) return number
with index context, scan context structure_ind_obj
using index_utl.mdlSubstructClobSearchFunction,
(blob, clob, integer) return number
with index context, scan context structure_ind_obj
using index_utl.mdlSubstructClobSearchFunction;

-- exact match search
create or replace operator exactmatch
binding 
(varchar2, varchar2, integer) return number
with index context, scan context structure_ind_obj
using index_utl.exactmatchSearchFunction,
(clob, varchar2, integer) return number
with index context, scan context structure_ind_obj
using index_utl.exactmatchSearchFunction,
(blob, varchar2, integer) return number
with index context, scan context structure_ind_obj
using index_utl.exactmatchSearchFunction;

-- similarity search
create or replace operator similarity
binding 
(varchar2, varchar2, number, integer) return number
with index context, scan context structure_ind_obj compute ancillary data
using index_utl.similaritySearchFunction,
(clob, varchar2, number, integer) return number
with index context, scan context structure_ind_obj compute ancillary data
using index_utl.similaritySearchFunction,
(blob, varchar2, number, integer) return number
with index context, scan context structure_ind_obj compute ancillary data
using index_utl.similaritySearchFunction;

-- Bindings for extended similarity domain operator
create or replace operator extended_similarity
binding 
(varchar2, varchar2, varchar2, varchar2, number, integer, number,  number) return number
with index context, scan context structure_ind_obj compute ancillary data
using index_utl.extendedSimilaritySearchFn,
(clob, varchar2, varchar2, varchar2, number, integer, number,  number) return number
with index context, scan context structure_ind_obj compute ancillary data
using index_utl.extendedSimilaritySearchFn,
(blob, varchar2, varchar2, varchar2, number, integer, number,  number) return number
with index context, scan context structure_ind_obj compute ancillary data
using index_utl.extendedSimilaritySearchFn;


-- ancillary operator for similarity score
create or replace operator similarityScore
binding
(number) return number
ancillary to similarity(varchar2, varchar2, number, integer),
             similarity(clob, varchar2, number, integer),
	         similarity(blob, varchar2, number, integer),
             extended_similarity(varchar2, varchar2, varchar2, varchar2, number, integer, number,  number),
             extended_similarity(clob, varchar2, varchar2, varchar2, number, integer, number,  number),
             extended_similarity(blob, varchar2, varchar2, varchar2, number, integer, number,  number) 
using index_utl.similarityScoreFunction;

-- 2D indextype
create or replace indextype structureIndexType
for substructure(varchar2, varchar2, integer),
    substructure(varchar2, clob, integer),
    mdl_substructure(varchar2, varchar2, integer),
    mdl_substructure(varchar2, clob, integer),
    mdl_clob_substructure(varchar2, clob, integer),
    exactmatch(varchar2, varchar2, integer),
    similarity(varchar2, varchar2, number, integer),
    substructure(clob, varchar2, integer),
    substructure(clob, clob, integer),
    mdl_substructure(clob, varchar2, integer),
    mdl_substructure(clob, clob, integer),
    mdl_clob_substructure(clob, clob, integer),
    exactmatch(clob, varchar2, integer),
    similarity(clob, varchar2, number, integer),
    substructure(blob, varchar2, integer),
    substructure(blob, clob, integer),
    mdl_substructure(blob, varchar2, integer),
    mdl_substructure(blob, clob, integer),
    mdl_clob_substructure(blob, clob, integer),
    exactmatch(blob, varchar2, integer),
    similarity(blob, varchar2, number, integer),
    extended_similarity(varchar2, varchar2, varchar2, varchar2, number, integer, number,  number),
    extended_similarity(clob, varchar2, varchar2, varchar2, number, integer, number,  number),
    extended_similarity(blob, varchar2, varchar2, varchar2, number, integer, number,  number) 
using structure_ind_obj;

