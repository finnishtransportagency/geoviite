set session geoviite.edit_user to 'MANUAL';
-- TODO: move constraints to V98 so they don't slow down the mass migration
-- TODO: This is now done by using alignment versioning throughout the layout graph. The other option is to give edges and nodes their own ids and versioning
drop table if exists layout.node_joint_version;
drop table if exists layout.edge_segment_version;
drop table if exists layout.edge_version;
drop table if exists layout.node_version;

create table layout.node_version
(
  alignment_id      int not null,
  alignment_version int not null,
  node_index        int not null,
  primary key (alignment_id, alignment_version, node_index),
  constraint node_version_alignment_version_fkey
    foreign key (alignment_id, alignment_version) references layout.alignment_version (id, version)
);
comment on table layout.node_version is 'A versioned list of nodes composing an alignment';

create table layout.edge_version
(
  alignment_id      int                             not null,
  alignment_version int                             not null,
  start_node_index  int                             not null,
  end_node_index    int                             not null generated always as (start_node_index + 1) stored,
  bounding_box      postgis.geometry(polygon, 3067) null,
  segment_count     int                             not null,
  length            decimal(13, 6)                  not null,
  primary key (alignment_id, alignment_version, start_node_index),
  constraint edge_version_alignment_version_fkey
    foreign key (alignment_id, alignment_version) references layout.alignment_version (id, version),
  constraint edge_version_start_node_version_fkey
    foreign key (alignment_id, alignment_version, start_node_index) references layout.node_version (alignment_id, alignment_version, node_index),
  constraint edge_version_end_node_version_fkey
    foreign key (alignment_id, alignment_version, end_node_index) references layout.node_version (alignment_id, alignment_version, node_index)
);
comment on table layout.edge_version is 'A versioned list of edges composing an alignment';

create table layout.edge_segment_version
(
  alignment_id           int                    not null,
  alignment_version      int                    not null,
  start_node_index       int                    not null,
  segment_index          int                    not null,
  geometry_alignment_id  int                    null,
  geometry_element_index int                    null,
  start                  decimal(13, 6)         not null,
  source_start           decimal(13, 6)         null,
  source                 layout.geometry_source not null,
  geometry_id            int                    not null references layout.segment_geometry (id),
  primary key (alignment_id, alignment_version, start_node_index, segment_index),
  constraint edge_segment_version_edge_version_fkey
    foreign key (alignment_id, alignment_version, start_node_index) references layout.edge_version (alignment_id, alignment_version, start_node_index),
  constraint edge_segment_version_geometry_alignment_fkey
    foreign key (geometry_alignment_id) references geometry.alignment (id),
  constraint edge_segment_version_geometry_element_fkey
    foreign key (geometry_alignment_id, geometry_element_index) references geometry.element (alignment_id, element_index)
);
comment on table layout.edge_segment_version is 'A versioned geometry segment in an alignment edge';

create table layout.node_joint_version
(
  alignment_id        int not null,
  alignment_version   int not null,
  node_index          int not null,
  switch_id           int not null, -- not a foreign key to switch/joint, because this version table is immutable and switch main table is not
  switch_joint_number int not null,
  primary key (alignment_id, alignment_version, switch_id, switch_joint_number),
  constraint node_joint_version_node_version_fkey
    foreign key (alignment_id, alignment_version, node_index) references layout.node_version (alignment_id, alignment_version, node_index)
);
comment on table layout.node_joint_version is 'A versioned layout-node to switch-joint link';
