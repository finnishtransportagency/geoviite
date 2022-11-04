create or replace function common.get_columns(main_schema_name varchar, main_table_name varchar)
  returns varchar as
$$
begin
  return (
    select string_agg(column_name, ', ' order by ordinal_position)
      from information_schema.columns
      where table_schema = main_schema_name and table_name = main_table_name
  );
end
$$ language plpgsql strict
                    immutable;


create or replace function common.get_prefixed_columns(prefix varchar, main_schema_name varchar, main_table_name varchar)
  returns varchar as
$$
begin
  return (
    select string_agg(concat(prefix, '.', column_name), ', ' order by ordinal_position)
      from information_schema.columns
      where table_schema = main_schema_name and table_name = main_table_name
  );
end
$$ language plpgsql strict
                    immutable;

create or replace function common.get_column_descriptions(main_schema_name varchar, main_table_name varchar)
  returns varchar as
$$
begin
  return (
    select
      string_agg(column_name || ' ' || udt_schema || '.' || udt_name ||
                 case
                   when character_maximum_length is not null then ' (' || character_maximum_length || ')'
                   else ''
                 end ||
                 case when is_nullable = 'YES' then ' null' else ' not null' end,
                 ', ' order by ordinal_position)
      from information_schema.columns
      where table_schema = main_schema_name and table_name = main_table_name
  );
end
$$ language plpgsql strict
                    immutable;


create or replace function common.get_prefixed_primary_key_description(prefix varchar, main_schema_name varchar, main_table_name varchar)
  returns varchar as
$$
begin
  return (
    select
      string_agg(prefix || '.' || column_name, ', ' order by ordinal_position)
      from information_schema.key_column_usage
      where table_schema = main_schema_name and table_name = main_table_name and constraint_name like '%_pkey'
  );
end
$$ language plpgsql strict
                    immutable;

create or replace function common.get_primary_key_description(main_schema_name varchar, main_table_name varchar)
  returns varchar as
$$
begin
  return (
    select string_agg(column_name, ', ' order by ordinal_position)
      from information_schema.key_column_usage
      where table_schema = main_schema_name and table_name = main_table_name and constraint_name like '%_pkey'
  );
end
$$ language plpgsql strict
                    immutable;

create or replace function common.nospace_lowercase(string varchar)
  returns varchar as
$$
begin
  return lower(regexp_replace(string, '\s', '', 'g'));
end
$$ language plpgsql strict
                    immutable;
