-- set session geoviite.edit_user to 'MANUAL';
-- TODO: move constraints to V98 so they don't slow down the mass migration
create or replace function layout.calculate_node_key(
  switch_links int[],
  start_track int,
  end_track int
) returns uuid language sql as $$
  select
    case
      when switch_links is not null then md5(row (switch_links, null, null)::text)::uuid
      when start_track is not null then md5(row (null, start_track, null)::text)::uuid
      when end_track is not null then md5(row (null, null, end_track)::text)::uuid
    end
$$ immutable;

create table layout.node
(
  id       int primary key generated always as identity,
  key      uuid not null unique
);
comment on table layout.node is 'Layout node: a connecting point in the layout graph. Immutable and un-versioned, this is really just an identity with no data.';

create table layout.node_switch_joint
(
  node_id      int not null references layout.node (id),
  -- TODO: could reference the official ID table, but not the current one since draft-only rows are deleted permanently
  -- TODO: could contain index (easy enough to produce) to maintain logical order of joints. Note, the hash is order-sensitive, so this should also maintain it
  switch_id int not null,
  switch_joint int not null, -- cannot reference joint-table as a current version of the switch might not exist
  constraint node_joint_unique unique (node_id, switch_id, switch_joint)
);

create table layout.node_track_end
(
  node_id      int not null references layout.node (id),
  -- TODO: could reference the official ID table, but not the current one since draft-only rows are deleted permanently
  starting_location_track_id int null unique,
  ending_location_track_id int null unique
);

-- TODO: Does this need a surrogate key? Do we actually even need this table at all?
create table layout.edge
(
  id       int primary key generated always as identity,
  start_node_id int not null references layout.node,
  end_node_id int not null references layout.node,
  constraint edge_nodes_unique unique (start_node_id, end_node_id)
);
comment on table layout.edge is 'Location track edge: a connection between two nodes of the layout graph. Immutable and un-versioned, this is really just an identity with no data.';

-- TODO: This could be an immutable table where we just write new rows (new ids), instead of updating the existing ones
-- That way we need one less table, can have stronger references, writing becomes faster and reusing the edges (unique by segment-geoms hash) is possible
create table layout.location_track_edge
(
  id            int primary key generated always as identity,
  edge_id int not null references layout.edge,
--   start_node_id int                             not null references layout.location_track_node (id),
--   end_node_id   int                             not null references layout.location_track_node (id),
  bounding_box  postgis.geometry(polygon, 3067) null,
  segment_count int                             not null,
  length        decimal(13, 6)                  not null
);
select common.add_table_metadata('layout', 'location_track_edge');
comment on table layout.location_track_edge is 'Location track edge: a geometry connecting two location track nodes';

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

-- This is a direct version-table under location_track versioning. It maintains a 1-many list of edges
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
