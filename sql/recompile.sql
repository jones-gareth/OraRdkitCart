prompt recompiling index common
alter package index_common compile package;
show errors;

prompt recomping index_base_obj body
alter type index_base_obj compile body;
show errors;
prompt recompiling structure_ind_obj body
alter type structure_ind_obj compile body;
show errors;

prompt recompiling index_utl package
alter package index_utl compile;
show errors;

prompt recompiling chem_structure pcakage
alter package chem_structure compile;
show errors;

