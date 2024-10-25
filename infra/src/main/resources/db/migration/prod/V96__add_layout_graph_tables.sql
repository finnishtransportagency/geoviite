-- set session geoviite.edit_user to 'MANUAL';
drop table if exists layout.node;
drop table if exists layout.node_version;
create table layout.node
(
  id       int primary key generated always as identity,
  geometry postgis.geometry(multipoint, 3067) not null
);
select common.add_table_metadata('layout', 'node');
comment on table layout.node is 'A node in the layout rail graph';

drop table if exists layout.edge;
drop table if exists layout.edge_version;
create table layout.edge
(
  start_node_id int                             not null,
  end_node_id   int                             not null,
  bounding_box  postgis.geometry(polygon, 3067) null,
  segment_count int                             not null,
  length        decimal(13, 6)                  not null,

  primary key (start_node_id, end_node_id)
);
select common.add_table_metadata('layout', 'edge');
comment on table layout.edge is 'An edge in the layout rail graph';
alter table layout.edge add constraint edge_id_version_unique unique (start_node_id, end_node_id, version);

drop table if exists layout.edge_segment_version;
drop table if exists layout.edge_segment_version_version;
create table layout.edge_segment_version
(
  start_node_id             int                                 not null,
  end_node_id               int                                 not null,
  segment_index             int                                 not null,
  edge_version              int                                 not null,
  geometry_alignment_id     int                                 null,
  geometry_element_index    int                                 null,
  start                     decimal(13, 6)                      not null,
  source_start              decimal(13, 6)                      null,
  source                    layout.geometry_source              not null,
  geometry_id               int                                 not null references layout.segment_geometry (id),

  primary key (start_node_id, end_node_id, segment_index),

  constraint edge_segment_version_edge_fkey
    foreign key (start_node_id, end_node_id, edge_version) references layout.edge (start_node_id, end_node_id, version),

  constraint edge_segment_version_geometry_alignment_fkey
    foreign key (geometry_alignment_id) references geometry.alignment (id),

  constraint edge_segment_version_geometry_element_fkey
    foreign key (geometry_alignment_id, geometry_element_index) references geometry.element (alignment_id, element_index)
);
comment on table layout.edge_segment_version is 'A versioned geometry segment in an edge in the layout rail graph';
