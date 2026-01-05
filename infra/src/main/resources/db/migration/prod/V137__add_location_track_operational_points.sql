create table layout.location_track_version_operational_point
(
  location_track_id                int  not null references layout.location_track_id (id),
  location_track_layout_context_id text not null,
  location_track_version           int  not null,
  operational_point_id             int  not null references layout.operational_point_id (id),

  constraint location_track_version_operational_point_pkey
    primary key (location_track_id, location_track_layout_context_id, location_track_version, operational_point_id),
  constraint location_track_version_operational_point_ltv_fkey
    foreign key (location_track_id,
                 location_track_layout_context_id,
                 location_track_version) references layout.location_track_version (id, layout_context_id, version)
);

create index location_track_version_operational_point_op_ix
  on layout.location_track_version_operational_point (operational_point_id);
