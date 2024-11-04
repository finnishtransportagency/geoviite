-- set session geoviite.edit_user to 'MANUAL';
-- TODO: move constraints to V98 so they don't slow down the mass migration

drop table if exists layout.node;
drop table if exists layout.node_version;
create table layout.node
(
  id       int primary key generated always as identity,
  geometry postgis.geometry(multipoint, 3067) not null
--   location postgis.geometry(point, 3067) not null
);
select common.add_table_metadata('layout', 'node');
comment on table layout.node is 'A node in the layout rail graph';

drop table if exists layout.node_switch_joint_version;
create table layout.node
(
  id       int primary key generated always as identity,
  geometry postgis.geometry(multipoint, 3067) not null
--   location postgis.geometry(point, 3067) not null
);
select common.add_table_metadata('layout', 'node');
comment on table layout.node is 'A node in the layout rail graph';

drop table if exists layout.edge;
drop table if exists layout.edge_version;
create table layout.edge
(
  id            int primary key generated always as identity,
  start_node_id int                             not null references layout.node (id),
  end_node_id   int                             not null references layout.node (id),
  bounding_box  postgis.geometry(polygon, 3067) null,
  segment_count int                             not null,
  length        decimal(13, 6)                  not null
);
select common.add_table_metadata('layout', 'edge');
comment on table layout.edge is 'An edge in the layout rail graph';
alter table layout.edge
  add constraint edge_id_version_unique unique (id, version);

drop table if exists layout.edge_segment_version;
create table layout.edge_segment_version
(
  edge_id                int                    not null,
  segment_index          int                    not null,
  edge_version           int                    not null,
  geometry_alignment_id  int                    null,
  geometry_element_index int                    null,
  start                  decimal(13, 6)         not null,
  source_start           decimal(13, 6)         null,
  source                 layout.geometry_source not null,
  geometry_id            int                    not null references layout.segment_geometry (id),
  primary key (edge_id, edge_version, segment_index),
  constraint edge_segment_version_edge_fkey
    foreign key (edge_id, edge_version) references layout.edge (id, version) deferrable initially deferred,
  constraint edge_segment_version_geometry_alignment_fkey
    foreign key (geometry_alignment_id) references geometry.alignment (id),
  constraint edge_segment_version_geometry_element_fkey
    foreign key (geometry_alignment_id, geometry_element_index) references geometry.element (alignment_id, element_index)
);
comment on table layout.edge_segment_version is 'A versioned geometry segment in an edge in the layout rail graph';

drop table if exists layout.location_track_edge_version;
create table layout.location_track_edge_version
(
  location_track_id      int not null,
  location_track_version int not null,
  edge_index             int not null,
  edge_id                int not null,
  edge_version           int not null,
  primary key (location_track_id, location_track_version, edge_index),
  constraint location_track_edge_edge_unique unique (location_track_id, edge_id),
  constraint location_track_edge_location_track_fkey
    foreign key (location_track_id) references layout.location_track (id),
  constraint location_track_edge_location_track_version_fkey
    foreign key (location_track_id, location_track_version) references layout.location_track_version (id, version),
  constraint location_track_edge_edge_fkey
    foreign key (edge_id) references layout.edge (id),
  constraint location_track_edge_edge_version_fkey
    foreign key (edge_id, edge_version) references layout.edge (id, version) deferrable initially deferred
);
comment on table layout.location_track_edge_version is 'A versioned list of edges composing a location track';

drop table if exists layout.switch_joint_version_2;
create table layout.switch_joint_version_2
(
  switch_id         int                           not null,
  switch_version    int                           not null,
  number            int                           not null,
  location          postgis.geometry(point, 3067) not null,
  location_accuracy common.location_accuracy      null,
--   node_id           int                           not null references layout.node (id),
  primary key (switch_id, switch_version, number),
  constraint switch_joint_switch_fkey
    foreign key (switch_id, switch_version) references layout.switch (id, version) deferrable initially deferred
);
comment on table layout.switch_joint_version_2 is 'A versioned joint in a layout switch';
