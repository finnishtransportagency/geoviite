create table layout.alignment
(
  id                    int primary key generated always as identity,
  geometry_alignment_id int                             null,
  bounding_box          postgis.geometry(polygon, 3067) null,
  segment_count         int                             not null,
  length                decimal(13, 6)                  not null
);

select common.add_metadata_columns('layout', 'alignment');
comment on table layout.alignment is 'Layout alignment: Single continuous line';
