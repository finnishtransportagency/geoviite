-- Implementation from https://wiki.postgresql.org/wiki/First/last_(aggregate)

-- Create a function that always returns the first non-NULL value:
create or replace function common.first_agg(anyelement, anyelement)
  returns anyelement
  language sql
  immutable strict parallel safe as
'select $1';

-- Then wrap an aggregate around it:
create or replace aggregate common.first (anyelement) (
  sfunc = common.first_agg
  , stype = anyelement
  , parallel = safe
  );

-- Create a function that always returns the last non-NULL value:
create or replace function common.last_agg(anyelement, anyelement)
  returns anyelement
  language sql
  immutable strict parallel safe as
'select $2';

-- Then wrap an aggregate around it:
create or replace aggregate common.last (anyelement) (
  sfunc = common.last_agg
  , stype = anyelement
  , parallel = safe
  );
