do $$
  declare
    min_version float = 16.3;
    current_version float = current_setting('server_version')::float;
  begin
    if current_version < min_version then
      raise exception 'This version of Geoviite supports PostgreSQL version %+. Current version is %.', min_version, current_version;
    end if;
  end $$;
