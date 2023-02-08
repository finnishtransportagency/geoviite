create or replace function common.create_timed_fetch_function(main_schema_name varchar, main_table_name varchar)
  returns void as
$$
declare
  version_table               varchar := format('%I.%I_version', main_schema_name, main_table_name);
  declare function_name       varchar := format('%I.%I_at', main_schema_name, main_table_name);
  declare version_select_sql  varchar :=
        'select distinct on (id) *' ||
        ' from ' || version_table ||
        ' where change_time <= $1' ||
        ' order by id, version desc';
  declare select_sql          varchar :=
        'select *' ||
        ' from (' || version_select_sql || ') versions' ||
        ' where deleted = false;';
  declare create_function_sql varchar :=
        'create or replace function ' || function_name || '(timestamptz) ' ||
        'returns setof ' || version_table ||
        ' as ''' || select_sql || '''' ||
        ' language sql stable;';
begin
  execute create_function_sql;
  return;
end;
$$ language plpgsql volatile;
