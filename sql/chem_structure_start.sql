
-- creates packages tables and types needed for structure searching.

-- cleanup 

-- drop type structure_search_hits_table;
-- drop type hit_table;
-- drop type similarity_hits;
-- drop sequence index_lookup_id_seq;
-- drop table index_lookup;
-- drop sequence row_change_id_seq;
-- drop table message_table;
-- drop sequence java_object_id_seq;
-- drop table java_objects;
-- drop table rmi_hostname;


-- to increase modify the size of batches sent to/from rmi change
-- these varray sizes (make sure all types have the same size though).

create or replace type intArray as varray(10000) of integer;
/

create or replace type stringArray as varray(10000) of varchar2(1000);
/


create or replace type paramArray as varray(50) of varchar2(1000);
/

-- types for index search

-- same type as sys.odciridlist
create or replace type rowIdArray as varray(32767) of varchar2(5072);
/

create or replace type doubleArray as varray(32767) of number;
/

create or replace type similarity_hit as object (
    hit_rowid varchar2(5072),
    score number);
/

create or replace type similarity_hits as table of similarity_hit;
/

-- pl/sql tables returned by search functions.


create or replace type 
structure_search_hit as object (
	id number,
	smiles varchar2(1000));
/

create or replace type 
structure_search_hits_table as table of
	structure_search_hit;
/

create or replace type hit_row as object (
	hit_rowid  varchar2(5072));
/

create or replace type 
hit_table as table of
	hit_row
/

-- lookup table for index change logs/journals

create sequence index_lookup_id_seq
       increment by 1
       start with 1
       nomaxvalue
       nocycle;

create table index_lookup (
	index_lookup_id number constraint index_lookup_pk primary key,
	index_key varchar2(100) not null,
	index_type varchar2(30) not null,
	change_table_name varchar2(30),
	indexname varchar2(30) not null,
	rmi_hostname varchar2(1000),
        constraint il_key_type_uniq unique (index_key, index_type));

-- create index index_lookup_key_ind on index_lookup(index_key);

-- sequence for row changes- shared by all log tables

create sequence row_change_id_seq
       increment by 1
       start with 1
       nomaxvalue
       nocycle;

-- log4j message table

create table message_table (
	time_logged timestamp,
	message varchar2(4000));


-- java objects table

create sequence java_object_id_seq
       increment by 1
       start with 1
       nomaxvalue
       nocycle;

create table java_objects (
    java_object_id number constraint java_obj_pk primary key,
    name varchar2(2000),
    value blob default empty_blob()
);

create unique index java_objects_name_ind on java_objects(name);

-- table to store RMI hostname

create table rmi_hostname (
	rmi_hostname varchar2(1000)
);

insert into rmi_hostname values('&rmiHost');


-- load in chem_structure package and structure type 
-- generate using get-package-source.pl
