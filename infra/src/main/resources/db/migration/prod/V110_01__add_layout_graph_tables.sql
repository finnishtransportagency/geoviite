create or replace function layout.calculate_node_hash(
  switch_in_id int,
  switch_in_joint_number int,
  switch_in_joint_role common.switch_joint_role,
  switch_out_id int,
  switch_out_joint_number int,
  switch_out_joint_role common.switch_joint_role,
  start_track int,
  end_track int
) returns uuid
  language sql as
$$
select
  case
    -- Hash by type so we don't need the nulls in the rows: this allows adding more types without changing existing hashes
    when switch_in_id is not null or switch_out_id is not null then
      md5(row
        (
        'SWITCH',
        switch_in_id,
        switch_in_joint_number,
        switch_in_joint_role,
        switch_out_id,
        switch_out_joint_number,
        switch_out_joint_role
        )::text
      )::uuid
    when start_track is not null then
      md5(row ('TRACK_START', start_track)::text)::uuid
    when end_track is not null then
      md5(row ('TRACK_END', end_track)::text)::uuid
  end
$$ immutable;

create type layout.node_type as enum ('SWITCH', 'TRACK_START', 'TRACK_END');
create table layout.node
(
  id                         int primary key generated always as identity,
  type                       layout.node_type         not null generated always as (
    case
      when starting_location_track_id is not null then 'TRACK_START'::layout.node_type
      when ending_location_track_id is not null then 'TRACK_END'::layout.node_type
      when switch_in_id is not null or switch_out_id is not null then 'SWITCH'::layout.node_type
      -- This fallback will fail the insert as the value cannot be null
      else null::layout.node_type
    end
    ) stored,
  hash                        uuid                     not null unique,
  -- TODO: GVT-2930: this could as well be a generated column, or even a unique constraint
  -- That does mean the migration is a little trickier and the insert function more verbose, though
--   generated always as (
--     layout.calculate_node_hash(
--         switch_in_id,
--         switch_in_joint_number,
--         switch_in_joint_role,
--         switch_out_id,
--         switch_out_joint_number,
--         switch_out_joint_role,
--         starting_location_track_id,
--         ending_location_track_id
--     )) stored,
  -- This cannot be an actual foreign key reference, as tracks are sometimes deleted and this table is immutable, like version tables
  switch_in_id               int                      null,
  switch_in_joint_number     int                      null,
  switch_in_joint_role       common.switch_joint_role null,
  switch_out_id              int                      null,
  switch_out_joint_number    int                      null,
  switch_out_joint_role      common.switch_joint_role null,
  starting_location_track_id int                      null unique,
  ending_location_track_id   int                      null unique,
  -- Unique constraint for enforcing type through foreign key references
  constraint node_type_unique unique (id, type),
  constraint chk_node_type check (
    -- Switch link must have a joint number (if id exists, joint must exist)
    ((switch_in_id is null) = (switch_in_joint_number is null)) and
    ((switch_out_id is null) = (switch_out_joint_number is null)) and
      -- Track end nodes cant be start and end at the same time
    (starting_location_track_id is null or ending_location_track_id is null) and
      -- Switch nodes are not track ends
    (
      (switch_in_id is null and switch_out_id is null) or
      (starting_location_track_id is null and ending_location_track_id is null)
      )
    )
);
comment on table layout.node is 'Layout node: a connecting point in the layout graph. Immutable and un-versioned, this is really just an identity with no data.';

create table layout.edge
(
  id            int primary key generated always as identity,
  start_node_id int                             not null references layout.node (id),
  end_node_id   int                             not null references layout.node (id),
  bounding_box  postgis.geometry(polygon, 3067) null,
  segment_count int                             not null,
  length        decimal(13, 6)                  not null,
  hash          uuid                            not null unique
);
comment on table layout.edge is 'Layout edge: an immutable geometry connecting two location track nodes';

create or replace function layout.calculate_segment_hash(
  geometry_alignment_id int,
  geometry_element_index int,
  source_start decimal(13, 6),
  source layout.geometry_source,
  geometry_id int
) returns uuid
  language sql as
$$
select md5(row (geometry_alignment_id, geometry_element_index, source_start, source, geometry_id)::text)::uuid
$$ immutable;

create or replace function layout.calculate_edge_hash(
  start_node_id int,
  end_node_id int,
  segment_hashes uuid[]
) returns uuid
  language sql as
$$
select md5(row (start_node_id, end_node_id, segment_hashes)::text)::uuid
$$ immutable;

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
  hash                   uuid                   not null generated always as (
    layout.calculate_segment_hash(
        geometry_alignment_id,
        geometry_element_index,
        source_start,
        source,
        geometry_id
    )) stored,
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
