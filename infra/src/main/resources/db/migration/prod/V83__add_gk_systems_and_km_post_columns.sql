create type layout.gk_location_source as enum ('FROM_GEOMETRY', 'FROM_LAYOUT', 'MANUAL');

alter table layout.km_post
  disable trigger version_update_trigger;
alter table layout.km_post
  disable trigger version_row_trigger;

alter table layout.km_post
  add column gk_location           postgis.geometry(point),
  add column gk_location_confirmed boolean,
  add column gk_location_source    layout.gk_location_source;

alter table layout.km_post_version
  add column gk_location           postgis.geometry(point),
  add column gk_location_confirmed boolean,
  add column gk_location_source    layout.gk_location_source;

