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

-- Ensure PostGIS extensions are at the latest available version before running migrations.
do
$$
  declare
    default_postgis_version text;
    current_postgis_version text;
  begin
    select default_version into default_postgis_version from pg_available_extensions where name = 'postgis';
    -- Verify postgis is available
    if default_postgis_version is null then
      raise exception 'PostGIS extension not found in pg_available_extensions';
      return;
    end if;

    select extversion into current_postgis_version from pg_extension where extname = 'postgis';
    -- Upgrade if not at the latest version
    if current_postgis_version is not null and current_postgis_version <> default_postgis_version then
      raise notice 'Upgrading PostGIS: old=% new=%', current_postgis_version, default_postgis_version;
      perform postgis.postgis_extensions_upgrade();
      select extversion into current_postgis_version from pg_extension where extname = 'postgis';
      raise notice 'PostGIS upgrade complete: version=%', current_postgis_version;
    end if;
  end
$$;
