alter table layout.segment_version
  add column length decimal(13,6) null,
  alter column start type decimal(13,6),
  alter column source_start type decimal(13,6);

update layout.segment_version
set length = postgis.st_m(postgis.st_endpoint(sg.geometry))
  from layout.segment_geometry sg
  where sg.id = layout.segment_version.geometry_id;

alter table layout.segment_version
  alter column length set not null;
