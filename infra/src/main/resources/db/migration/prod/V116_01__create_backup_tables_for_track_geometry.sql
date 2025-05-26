create table deprecated.location_track_version_alignment
(
  location_track_id                int         not null,
  location_track_layout_context_id int         not null,
  location_track_version           int         not null,
  alignment_id                     int         not null,
  alignment_version                int         not null,
  change_user                      varchar(30) not null,
  change_time                      timestamptz not null,
  deleted                          boolean     not null,

  primary key (location_track_id, location_track_layout_context_id, location_track_version),
  foreign key (alignment_id, alignment_version) references deprecated.alignment_version (id, version)
);

create table deprecated.alignment_version
(
  id            int                             not null,
  version       int                             not null,
  bounding_box  postgis.geometry(polygon, 3067) null,
  segment_count int                             not null,
  length        decimal(13, 6)                  not null,
  change_user   varchar(30)                     not null,
  change_time   timestamptz                     not null,
  deleted       boolean                         not null,

  primary key (id, version)
);

create table deprecated.alignment_version_segment
(
  alignment_id              int                    not null,
  alignment_version         int                    not null,
  segment_index             int                    not null,
  geometry_alignment_id     int                    null,
  geometry_element_index    int                    null,
  switch_id                 int                    null,
  switch_start_joint_number int                    null,
  switch_end_joint_number   int                    null,
  start_m                   decimal(13, 6)         not null,
  source_start_m            decimal(13, 6)         null,
  source                    layout.geometry_source not null,
  geometry_id               int                    not null references layout.segment_geometry (id),

  primary key (alignment_id, alignment_version, segment_index),
  foreign key (alignment_id, alignment_version) references deprecated.alignment_version (id, version)
);
