create table layout.node
(
  id   int primary key generated always as identity,
  -- Both ports of a node must be of the same type. This column is used to enforce that via foreign keys
  type layout.node_type not null,
  hash uuid             not null unique,
  -- Unique constraint for enforcing type through foreign key references
  constraint node_type_unique unique (id, type)
);
comment on table layout.node is 'Layout node: a connecting point in the layout graph. Immutable and un-versioned, this is really just an identity for the node data.';

create table layout.node_port
(
  node_id                    int                      not null references layout.node (id),
  port                       layout.node_port_type    not null,
  node_type                  layout.node_type         not null generated always as (
    case
      when boundary_location_track_id is not null and switch_id is null then 'TRACK_BOUNDARY'::layout.node_type
      else 'SWITCH'::layout.node_type
    end
  ) stored,
  hash                       uuid                     not null,
  -- This cannot be an actual foreign key reference, as tracks are sometimes deleted and this table is immutable, like version tables
  switch_id                  int                      null,
  switch_joint_number        int                      null,
  switch_joint_role          common.switch_joint_role null,
  boundary_location_track_id int                      null,
  boundary_type              layout.boundary_type     null,
  primary key (node_id, port),
  constraint node_half_node_fkey foreign key (node_id, node_type) references layout.node (id, type),
  constraint chk_node_type check (
    -- Note: the switch might be missing even if the type is SWITCH and the port exists
    -- Switch link must have a joint number & role (if id exists, joint/role must exist)
    ((switch_id is null) = (switch_joint_number is null)) and
    ((switch_id is null) = (switch_joint_role is null)) and
    -- Track boundaries must have id & type
    (node_type <> 'TRACK_BOUNDARY' or (boundary_location_track_id is not null and boundary_type is not null))
  )
);
comment on table layout.node_port is 'Layout node port: a node consists of 1-2 ports for edge connections. The concept offers no particular interpretation for one vs the other, so they are just A and B. The content is always sorted by IDs so that A port is guaranteed to exist and a node with 2 ports is always the same node, regardless of content order.';

create table layout.edge
(
  id              int primary key generated always as identity,
  start_node_id   int                             not null references layout.node (id),
  start_node_port layout.node_port_type           not null,
  end_node_id     int                             not null references layout.node (id),
  end_node_port   layout.node_port_type           not null,
  bounding_box    postgis.geometry(polygon, 3067) not null,
  start_location  postgis.geometry(point, 3067)   not null,
  end_location    postgis.geometry(point, 3067)   not null,
  segment_count   int                             not null,
  length          decimal(13, 6)                  not null,
  hash            uuid                            not null unique,
  constraint edge_start_node_port_fkey foreign key (start_node_id, start_node_port) references layout.node_port (node_id, port),
  constraint edge_end_node_port_fkey foreign key (end_node_id, end_node_port) references layout.node_port (node_id, port)
);
comment on table layout.edge is 'Layout edge: an immutable geometry connecting two location track nodes';

create table layout.edge_segment
(
  edge_id                int                    not null references layout.edge (id),
  segment_index          int                    not null,
  geometry_alignment_id  int                    null,
  geometry_element_index int                    null,
  start                  decimal(13, 6)         not null,
  source_start           decimal(13, 6)         null,
  source                 layout.geometry_source not null,
  geometry_id            int                    not null references layout.segment_geometry (id),
  hash                   uuid                   not null,
  primary key (edge_id, segment_index),
  constraint edge_segment_version_geometry_alignment_fkey
    foreign key (geometry_alignment_id) references geometry.alignment (id),
  constraint edge_segment_version_geometry_element_fkey
    foreign key (geometry_alignment_id, geometry_element_index) references geometry.element (alignment_id, element_index)
);
comment on table layout.edge_segment is 'A geometry segment (length with a unified metadata) in a layout edge';

-- This is a direct version-table under location_track versioning. It maintains a 1-many list of edges
create table layout.location_track_version_edge
(
  location_track_id                int            not null,
  location_track_layout_context_id varchar        not null,
  location_track_version           int            not null,
  edge_index                       int            not null,
  edge_id                          int            not null references layout.edge (id),
  start_m                          decimal(13, 6) not null,
  primary key (location_track_id, location_track_layout_context_id, location_track_version, edge_index),
  constraint location_track_edge_version_location_track_fkey
    foreign key (location_track_id, location_track_layout_context_id, location_track_version)
      references layout.location_track_version (id, layout_context_id, version)
);
comment on table layout.location_track_version_edge is 'Versioned 1-to-many linking for edges composing a location track version';

create table layout.initial_edge_segment_metadata
(
  edge_id       int not null references layout.edge (id),
  segment_index int not null,
  metadata_id   int not null references layout.initial_import_metadata (id),
  primary key (edge_id, segment_index),
  constraint initial_edge_segment_metadata_edge_segment_fkey
    foreign key (edge_id, segment_index) references layout.edge_segment (edge_id, segment_index)
);
comment on table layout.initial_edge_segment_metadata is 'Initial import metadata links for edge segments';
