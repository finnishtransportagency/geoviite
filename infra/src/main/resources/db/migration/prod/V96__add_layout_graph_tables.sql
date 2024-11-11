-- set session geoviite.edit_user to 'MANUAL';
-- TODO: move constraints to V98 so they don't slow down the mass migration
-- TODO: This is now done by using alignment versioning throughout the layout graph. The other option is to give edges and nodes their own ids and versioning
-- drop table if exists layout.location_track_edge_ref_version;
-- drop table if exists layout.location_track_edge_segment_version;
-- drop table if exists layout.location_track_edge_version;
-- drop table if exists layout.location_track_edge;
-- drop table if exists layout.location_track_node_ref_version;
-- drop table if exists layout.location_track_node_switch_version;
-- drop table if exists layout.location_track_node_version;
-- drop table if exists layout.location_track_node;

create table layout.location_track_node
(
  id       int primary key generated always as identity,
  location postgis.geometry(point, 3067) not null
);
select common.add_table_metadata('layout', 'location_track_node');
-- alter table layout.location_track_node add constraint location_track_node_id_version_unique unique (id, version);
comment on table layout.location_track_node is 'Location track node: a connecting point for switches and other tracks within a location track';

-- This is a direct version-table under node versioning
-- Hence it cannot reference main tables directly as rows in those can be deleted
create table layout.location_track_node_switch_version
(
  node_id      int not null,
  node_version int not null,
  -- TODO: We could make this a versioned link that does not update when the switch is updated (version at-time-of-linking)
  switch_id    int not null,
  switch_joint int not null,
  primary key (node_id, node_version, switch_id, switch_joint),
  constraint location_track_node_switch_node_version_fkey foreign key (node_id, node_version) references layout.location_track_node_version (id, version)
);
comment on table layout.location_track_node_switch_version is 'Versioned 1-to-many linking of switch nodes to location track node';

create table layout.location_track_edge
(
  id            int primary key generated always as identity,
  start_node_id int                             not null references layout.location_track_node (id),
  end_node_id   int                             not null references layout.location_track_node (id),
  bounding_box  postgis.geometry(polygon, 3067) null,
  segment_count int                             not null,
  length        decimal(13, 6)                  not null
);
select common.add_table_metadata('layout', 'location_track_edge');
comment on table layout.location_track_edge is 'Location track edge: a geometry connecting two location track nodes';
-- alter table layout.location_track_node add constraint location_track_edge_id_version_unique unique (id, version);

-- This is a direct version-table under edge versioning
-- Hence it cannot reference main tables directly as rows in those can be deleted
create table layout.location_track_edge_segment_version
(
  edge_id                int                    not null,
  edge_version           int                    not null,
  segment_index          int                    not null,
  geometry_alignment_id  int                    null,
  geometry_element_index int                    null,
  start                  decimal(13, 6)         not null,
  source_start           decimal(13, 6)         null,
  source                 layout.geometry_source not null,
  geometry_id            int                    not null references layout.segment_geometry (id),
  primary key (edge_id, edge_version, segment_index),
  constraint location_track_edge_segment_version_edge_version_fkey
    foreign key (edge_id, edge_version) references layout.location_track_edge_version (id, version),
  constraint edge_segment_version_geometry_alignment_fkey
    foreign key (geometry_alignment_id) references geometry.alignment (id),
  constraint edge_segment_version_geometry_element_fkey
    foreign key (geometry_alignment_id, geometry_element_index) references geometry.element (alignment_id, element_index)
);
comment on table layout.location_track_edge_segment_version is 'A versioned geometry segment in an alignment edge';

-- Ref-tables for 1-many links in locationtrack <-> node/edge

-- This is a direct version-table under location_track versioning
create table layout.location_track_node_ref_version
(
  location_track_id      int not null,
  location_track_version int not null,
  node_index             int not null,
  node_id                int not null,
  node_version           int not null,
  constraint location_track_node_ref_node_fkey
    foreign key (node_id, node_version) references layout.location_track_node_version (id, version)
      deferrable initially deferred
);
comment on table layout.location_track_node_ref_version is 'Versioned 1-to-many linking for locationtrack nodes';

-- This is a direct version-table under location_track versioning
create table layout.location_track_edge_ref_version
(
  location_track_id      int not null,
  location_track_version int not null,
  edge_index             int not null,
  edge_id                int not null,
  edge_version           int not null,
-- Versioned ref: if the edge is updated, these refs need to be updated as well
  constraint location_track_edge_ref_edge_fkey
    foreign key (edge_id, edge_version) references layout.location_track_edge_version (id, version)
      deferrable initially deferred
);
comment on table layout.location_track_edge_ref_version is 'Versioned 1-to-many linking for locationtrack edges';
