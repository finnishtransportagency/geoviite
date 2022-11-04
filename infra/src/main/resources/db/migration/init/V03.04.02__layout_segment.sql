create table layout.segment
(
  alignment_id              int                                 not null,
  alignment_version         int                                 not null,
  segment_index             int                                 not null,
  geometry_alignment_id     int                                 null,
  geometry_element_index    int                                 null,
  geometry                  postgis.geometry(linestringm, 3067) not null,
  resolution                int                                 not null,
  height_values             decimal(10, 6)[]                    null,
  cant_values               decimal(10, 6)[]                    null,
  switch_id                 int                                 null,
  switch_start_joint_number int                                 null,
  switch_end_joint_number   int                                 null,
  start                     decimal(13, 6)                      not null,
  length                    decimal(13, 6)                      not null,
  source_start              decimal(13, 6)                      null,
  source                    layout.geometry_source              not null,
  primary key (alignment_id, segment_index)
);

select common.add_metadata_columns('layout', 'segment');
comment on table layout.segment is 'Layout segment: length of layout.alignment as a polyline with metadata.';
