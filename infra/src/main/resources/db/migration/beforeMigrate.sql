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
-- Only runs if PostGIS is already installed but not yet at the expected version.
do
$$
  declare
    expected_postgis_version text = '3.5.1';
    current_postgis_version  text;
  begin
    select extversion into current_postgis_version from pg_extension where extname = 'postgis';
    if current_postgis_version is not null and current_postgis_version <> expected_postgis_version then
      raise notice 'Upgrading PostGIS from % to %', current_postgis_version, expected_postgis_version;
      perform postgis.postgis_extensions_upgrade();
      -- Log the actual installed version after the upgrade to keep notices accurate.
      select extversion into current_postgis_version from pg_extension where extname = 'postgis';
      raise notice 'PostGIS upgrade complete, current version is %', current_postgis_version;
    end if;
  end
$$;
