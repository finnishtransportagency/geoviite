drop index if exists layout.switch_version_geometry_switch_version_ix;
create index switch_version_geometry_switch_version_ix on layout.switch_version (geometry_switch_id, version);
