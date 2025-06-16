/*create or replace function common.create_timed_fetch_function(main_schema_name varchar, main_table_name varchar)
  returns void as
$$
declare
  version_table               varchar := format('%I.%I_version', main_schema_name, main_table_name);
  declare function_name       varchar := format('%I.%I_at', main_schema_name, main_table_name);
  declare select_sql          varchar :=
    'select *' ||
    ' from ' || version_table ||
    ' where change_time >= $1 and (version_end_time is null or version_end_time > $1) and deleted=false;';
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
*/
