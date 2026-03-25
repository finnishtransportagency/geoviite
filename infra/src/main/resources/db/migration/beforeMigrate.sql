do
$$
  declare
    current_major int = current_setting('server_version_num')::int / 10000;
    current_minor int = current_setting('server_version_num')::int % 10000;
    min_major     int = 16;
    min_minor     int = 11;
  begin
    if current_major < min_major or (current_major = min_major and current_minor < min_minor) then
      raise exception 'This version of Geoviite supports PostgreSQL version %.%+. Current version is %.%.',
        min_major, min_minor, current_major, current_minor;
    end if;
  end
$$;
