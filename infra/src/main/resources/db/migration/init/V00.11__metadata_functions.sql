create or replace function common.version_update()
  returns trigger as
$$
declare
  prev_version int;
begin
  if (old.version is null) then
    execute
      format('select %I.get_%I_version(' || (
        select common.get_prefixed_primary_key_description('$1', tg_table_schema::varchar, tg_table_name::varchar)
      ) || ')', tg_table_schema, tg_table_name)
      using new
      into prev_version;
  else
    prev_version = old.version;
  end if;

  new.version = 1 + prev_version;
  new.change_user = current_setting('geoviite.edit_user');
  new.change_time = now();
  if new.change_user is null or new.change_user = '' then
    raise exception 'cannot edit data without change user';
  end if;
  return new;
end
$$ language plpgsql;

create or replace function common.add_version_row()
  returns trigger as
$$
declare
  insert_columns varchar := (
    select common.get_columns(tg_table_schema::varchar, tg_table_name::varchar)
  );
  select_columns varchar := (
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

    execute format('insert into %I.%I_version (%s, deleted) select %s, true',
                   tg_table_schema, tg_table_name, insert_columns, select_columns) using old;
    return old;
  else
    execute format('insert into %I.%I_version (%s, deleted) select %s, false',
      tg_table_schema, tg_table_name, insert_columns, select_columns) using new;
    return new;
  end if;
end
$$ language plpgsql;

create or replace function common.create_version_fetch_function(main_schema_name varchar, main_table_name varchar)
  returns void as
$$
declare
  function_params             varchar := (
    select
      string_agg(usage.column_name || '_arg' || ' ' || udt_schema || '.' || udt_name,
                 ', ' order by usage.ordinal_position)
      from information_schema.key_column_usage usage
        left join information_schema.columns
                  on usage.table_schema = columns.table_schema
                    and usage.table_name = columns.table_name
                    and usage.column_name = columns.column_name
      where columns.table_schema = main_schema_name
        and columns.table_name = main_table_name
        and usage.constraint_name like '%_pkey'
  );
  where_condition             varchar := (
    select string_agg(column_name || '=' || column_name || '_arg', ' and ' order by ordinal_position)
      from information_schema.key_column_usage
      where table_schema = main_schema_name and table_name = main_table_name and constraint_name like '%_pkey'
  );
  select_clause               varchar := format(
        'select coalesce(max(version),0) from %I.%I_version where ' || where_condition,
        main_schema_name, main_table_name, where_condition
    );
  declare create_function_sql varchar := format(
                  'create or replace function %I.get_%I_version(' || function_params || ')'
              || ' returns int as '' begin'
            || ' return (' || select_clause || ');'
      || ' end '' language plpgsql strict immutable;',
                  main_schema_name, main_table_name);
begin
  execute create_function_sql;
  return;
end;
$$ language plpgsql strict
                    volatile;

create or replace function common.create_version_update_trigger(main_schema_name varchar, main_table_name varchar)
  returns void as
$$
declare
  drop_sql_str           varchar := format(
      'drop trigger if exists version_update_trigger on %I.%I',
      main_schema_name, main_table_name);
  declare create_sql_str varchar := format(
      'create trigger version_update_trigger before insert or update on %I.%I ' ||
      'for each row execute function common.version_update();',
      main_schema_name, main_table_name);
begin
  execute drop_sql_str;
  execute create_sql_str;
  return;
end;
$$ language plpgsql volatile;

create or replace function common.create_version_row_trigger(main_schema_name varchar, main_table_name varchar)
  returns void as
$$
declare
  drop_sql_str           varchar := format(
      'drop trigger if exists version_row_trigger on %I.%I',
      main_schema_name, main_table_name);
  declare create_sql_str varchar := format(
      'create trigger version_row_trigger after insert or update or delete on %I.%I ' ||
      'for each row execute function common.add_version_row();',
      main_schema_name, main_table_name);
begin
  execute drop_sql_str;
  execute create_sql_str;
  return;
end;
$$ language plpgsql volatile;

create or replace function common.create_version_table(main_schema_name varchar, main_table_name varchar)
  returns void as
$$
  declare column_descs varchar := (
    select common.get_column_descriptions(main_schema_name, main_table_name)
  );
  declare column_names varchar := (
    select common.get_columns(main_schema_name, main_table_name)
  );
  declare primary_key varchar := (
    select common.get_primary_key_description(main_schema_name, main_table_name)
  );
  declare sql_str varchar := format(
    'create table if not exists %I.%I_version (' || column_descs || ', deleted boolean not null, primary key (' || primary_key || ', version));',
    main_schema_name, main_table_name
  );
  declare comment_str varchar := format(
    'comment on table %I.%I_version is ''Version table for %I.%I'';',
    main_schema_name, main_table_name, main_schema_name, main_table_name
  );
  declare copy_str varchar := format(
    'insert into %I.%I_version (' || column_names || ', deleted) select ' || column_names || ', false as deleted from %I.%I;',
    main_schema_name, main_table_name, main_schema_name, main_table_name
  );
begin
  execute sql_str;
  execute comment_str;
  execute copy_str;
  return;
end;
$$ language plpgsql volatile;

create or replace function common.add_metadata_columns(main_schema_name varchar, main_table_name varchar)
  returns void as
$$
declare
  alter_sql_str varchar := format('alter table %I.%I ' ||
                                  'add column version integer not null default 1, ' ||
                                  'add column change_user varchar(30) not null constraint change_user_non_empty check(length(change_user)>0) default current_setting(''geoviite.edit_user''), ' ||
                                  'add column change_time timestamptz not null default now();',
                                  main_schema_name, main_table_name);
begin
  execute alter_sql_str;
  return;
end;
$$ language plpgsql volatile;

create or replace function common.add_table_metadata(main_schema_name varchar, main_table_name varchar)
  returns void as
$$
begin
  perform common.add_metadata_columns(main_schema_name, main_table_name);
  perform common.add_table_versioning(main_schema_name, main_table_name);
  return;
end;
$$ language plpgsql volatile;

create or replace function common.add_table_versioning(main_schema_name varchar, main_table_name varchar)
  returns void as
$$
begin
  perform common.create_version_table(main_schema_name, main_table_name);
  perform common.create_version_fetch_function(main_schema_name, main_table_name);
  perform common.create_version_update_trigger(main_schema_name, main_table_name);
  perform common.create_version_row_trigger(main_schema_name, main_table_name);
  return;
end;
$$ language plpgsql volatile;
