/*
 We use an empty type, or rather setof empty_type, as the return type of table-valued functions used as boolean
 functions (necessary in PostgreSQL which inlines scalar and table-valued functions differently). Any actual columns
 in the return value would have to be arbitrary and would complicate queries: All we actually need is a distinction
 between a zero-row or one-row empty table.

 Unfortunately PostgreSQL doesn't actually allow you to declare an empty type. It does, however, allow declaring an
 empty table and using it as a type.
 */
create table empty_type ();
