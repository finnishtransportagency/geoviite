drop index if exists layout.km_post_version_geometry_km_post_version_ix;
create index km_post_version_geometry_km_post_version_ix on layout.km_post_version (geometry_km_post_id, version);
