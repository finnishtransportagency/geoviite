-- Note: the new expiry_time column makes the old deleted flag redundant to a degree. We could just
-- not have the deleted row in the table to indicate it's non-existence. However, there's a couple of
-- issues with that approach:
--
-- 1. We'd need to ensure that the metadata of the deletion is preserved in the previous row, so we'd
-- have to duplicate the change_user (and any future metadata columns) into the previous row as
-- 'expiry_user' etc.
-- 2. We'd need to compress the versions after removing the deleted=true rows, as logic often assumes
-- that the next version can be fetched with current.version+1. This also means that we'd need to
-- update any references to the versions. References to version tables for deletable data isn't that
-- common, so this should be feasible, but it does add complexity to the migration.

-- To avoid these issues, we still keep the deleted-flag and the deleted rows in the version table.

-- New version table creation function that includes expiry_time
create or replace function common.create_version_table(main_schema_name varchar, main_table_name varchar)
  returns void as
$$
declare
  column_descs varchar := (
    select common.get_column_descriptions(main_schema_name, main_table_name)
  );
  column_names varchar := (
    select common.get_columns(main_schema_name, main_table_name)
  );
  primary_key  varchar := (
    select common.get_primary_key_description(main_schema_name, main_table_name)
  );
  sql_str      varchar :=
    format(
        'create table if not exists %I.%I_version (' || column_descs ||
        ', deleted boolean not null, expiry_time timestamptz null,' ||
        ' primary key (' || primary_key || ', version));',
        main_schema_name, main_table_name
    );
  comment_str  varchar :=
    format(
        'comment on table %I.%I_version is ''Version table for %I.%I'';',
        main_schema_name, main_table_name, main_schema_name, main_table_name
    );
  copy_str     varchar :=
    format(
        'insert into %I.%I_version (' || column_names || ', deleted, expiry_time) select ' || column_names ||
        ', false as deleted, null as expiry_time from %I.%I;',
        main_schema_name, main_table_name, main_schema_name, main_table_name
    );
begin
  execute sql_str;
  execute comment_str;
  execute copy_str;
  return;
end;
$$ language plpgsql volatile;

-- New version trigger functions that add also update the version expiry_time to previous version
-- when a new one is created
create or replace function common.add_version_row()
  returns trigger as
$$
declare
  primary_key_where_condition varchar := (
    select
      string_agg('$1.' || column_name || '=' || 'prev_version.' || column_name, ' and '
                 order by ordinal_position)
      from information_schema.key_column_usage
      where table_schema = tg_table_schema::varchar
        and table_name = tg_table_name::varchar
        and constraint_name like '%_pkey'
  );
  insert_columns              varchar := (
    select common.get_columns(tg_table_schema::varchar, tg_table_name::varchar)
  );
  select_columns              varchar := (
    select common.get_prefixed_columns('$1', tg_table_schema::varchar, tg_table_name::varchar)
  );
begin
  if (tg_op = 'DELETE') then
    old.version = 1 + old.version;
    old.change_user = current_setting('geoviite.edit_user');
    old.change_time = now();
    if old.change_user is null or old.change_user = '' then
      raise exception 'cannot delete data without change user';
    end if;
    -- Set the previous version's expiry time to the current change time
    execute
      format(
          'update %I.%I_version prev_version set expiry_time = $1.change_time where '
            || primary_key_where_condition
            || ' and $1.version-1=prev_version.version',
          tg_table_schema, tg_table_name
      ) using old;
    -- Insert the new delete-version row without expiry_time
    execute format('insert into %I.%I_version (%s, deleted, expiry_time) select %s, true, null',
                   tg_table_schema, tg_table_name, insert_columns, select_columns) using old;
    return old;
  else
    -- Set the previous version's expiry time to the current change time
    execute
      format(
          'update %I.%I_version prev_version set expiry_time = $1.change_time where '
            || primary_key_where_condition
            || ' and $1.version-1=prev_version.version',
          tg_table_schema, tg_table_name
      ) using new;
    -- Insert the new version row without expiry_time
    execute
      format(
          'insert into %I.%I_version (%s, deleted, expiry_time) select %s, false, null',
          tg_table_schema, tg_table_name, insert_columns, select_columns
      ) using new;
    return new;
  end if;
end
$$ language plpgsql;

-- Updated timed fetch function that uses the new expiry_time column instead of window functions
-- Note: we don't automatically create indexes for all version tables as all needs are different,
-- so if this feels slow, you can try adding an index with [change_time, expiry_time, deleted] to the version table.
create or replace function common.create_timed_fetch_function(main_schema_name varchar, main_table_name varchar)
  returns void as
$$
declare
  version_table       varchar := format('%I.%I_version', main_schema_name, main_table_name);
  function_name       varchar := format('%I.%I_at', main_schema_name, main_table_name);
  -- End time is exclusive, to get distinct results
  -- Note: if there is a second version with the same change_time, then the first version's expiry_time will be equal to
  -- the change time. That means that the end-exclusive search will skip that row and return only the newer one. Hence,
  -- we don't need to sort/distinct to deduplicate versions even when multiple have the same change_time.
  select_sql          varchar :=
    'select * from ' || version_table ||
    ' where $1 >= change_time and $1 < expiry_time and deleted = false;';
  create_function_sql varchar :=
    'create or replace function ' || function_name || '(timestamptz) ' ||
    'returns setof ' || version_table ||
    ' as ''' || select_sql || '''' ||
    ' language sql stable;';
begin
  execute create_function_sql;
  return;
end;
$$ language plpgsql volatile;

-- A new function for recreating the version trigger functions to refresh functionality
create or replace function common.refresh_versioning_functions(main_schema_name varchar, main_table_name varchar)
  returns void as
$$
begin
  perform common.create_version_fetch_function(main_schema_name, main_table_name);
  perform common.create_version_update_trigger(main_schema_name, main_table_name);
  perform common.create_version_row_trigger(main_schema_name, main_table_name);
  perform common.create_timed_fetch_function(main_schema_name, main_table_name);
  return;
end;
$$ language plpgsql volatile;

-- New function for altering version tables from deprecated format to the new one:
-- * Add version expiry column
-- * Set existing expiry times based on next version's change time
-- * Current deleted-flag is retained and deleted rows are not removed (they get expiry_time as any other row)
create or replace function common.add_version_expiry_times(main_schema_name varchar, main_table_name varchar)
  returns void as
$$
declare
  declare
  add_expiry_time_column_sql   text    :=
    format(
        'alter table %I.%I_version add column expiry_time timestamptz null;',
        main_schema_name, main_table_name
    );
  primary_key_where_condition  varchar := (
    select
      string_agg('version_row.' || column_name || '=' || 'other_version_row.' || column_name, ' and '
                 order by ordinal_position)
      from information_schema.key_column_usage
      where table_schema = main_schema_name and table_name = main_table_name and constraint_name like '%_pkey'
  );
  select_next_change_time_sql  text    :=
    format(
        'select other_version_row.change_time from %I.%I_version other_version_row where '
          || primary_key_where_condition
          || ' and version_row.version+1=other_version_row.version',
        main_schema_name, main_table_name
    );
  set_existing_expiry_time_sql text    :=
    format(
        'update %I.%I_version version_row set expiry_time = (' || select_next_change_time_sql || ') where 1=1;',
        main_schema_name, main_table_name
    );
begin
  execute add_expiry_time_column_sql;
  execute set_existing_expiry_time_sql;
  perform common.refresh_versioning_functions(main_schema_name, main_table_name);
  return;
end;
$$ language plpgsql strict
                    volatile;
