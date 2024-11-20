-- set session geoviite.edit_user to 'MANUAL';
-- TODO: move constraints to V98 so they don't slow down the mass migration

create type layout.node_type as enum ('SWITCH', 'TRACK_START', 'TRACK_END');
create table layout.node
(
  id                         int primary key generated always as identity,
  type                       layout.node_type not null generated always as (
    case
      when starting_location_track_id is not null then 'TRACK_START'::layout.node_type
      when ending_location_track_id is not null then 'TRACK_END'::layout.node_type
      else 'SWITCH'::layout.node_type
    end
    ) stored,
  key                        uuid             not null unique,
  starting_location_track_id int              null unique,
  ending_location_track_id   int              null unique,
  -- Unique constraint for enforcing type through foreign key references
  constraint node_type_unique unique (id, type),
  constraint chk_node_type check (
    starting_location_track_id is null or ending_location_track_id is null
  )
);
comment on table layout.node is 'Layout node: a connecting point in the layout graph. Immutable and un-versioned, this is really just an identity with no data.';

create table layout.node_switch_joint
(
  node_id      int not null references layout.node (id),
  -- Dummy column for enforcing node type via foreign key
  node_type    layout.node_type not null generated always as ('SWITCH'::layout.node_type) stored,
  -- TODO: could reference the official ID table, but not the current one since draft-only rows are deleted permanently
  -- TODO: could contain index (easy enough to produce) to maintain logical order of joints. Note, the hash is order-sensitive, so this should also maintain it
  switch_id    int not null,
  switch_joint int not null, -- cannot reference joint-table as a current version of the switch might not exist
  constraint node_joint_unique unique (node_id, switch_id, switch_joint),
  foreign key (node_id, node_type) references layout.node (id, type)
);

create or replace function layout.calculate_node_key(
  switch_ids int[],
  switch_joints int[],
  start_track int,
  end_track int
) returns uuid
  language sql as
$$
select
  case
    -- Key by type so we don't need the nulls in the rows: this allows adding more types without changing existing keys
    when switch_ids is not null then md5(row ('SWITCH', switch_ids, switch_joints)::text)::uuid
    when start_track is not null then md5(row ('TRACK_START', start_track)::text)::uuid
    when end_track is not null then md5(row ('TRACK_END', end_track)::text)::uuid
    -- TODO: add option for reference line
  end
$$ immutable;

create or replace function layout.get_or_insert_node(
  switch_ids int[],
  switch_joints int[],
  start_track int,
  end_track int
) returns int as
$$
  declare
    new_key         uuid := (
      select layout.calculate_node_key(switch_ids, switch_joints, start_track, end_track)
    );
  declare result_id int;
begin

  -- Try inserting node: if it already exists, the key will conflict
  insert into layout.node
    (key)
    values
      (new_key)
  on conflict do nothing
    returning id into result_id;

  -- If the row was inserted (no conflict) then the id is not null -> insert the rest of the data
  if result_id is not null then
    -- Only check on actual insert, to keep the fetch quicker
    if array_length(switch_ids, 1) is distinct from array_length(switch_joints, 1) then
      raise exception 'switch_ids and switch_joints must match (have the same length)';
    end if;
    if switch_ids is not null and (start_track is not null or end_track is not null) then
      raise exception 'a node can only have either switches or tracks, not both';
    end if;
    if start_track is not null and end_track is not null then
      raise exception 'a node can only have either a starting or an ending track, not both';
    end if;

    if switch_ids is not null then
      insert into layout.node_switch_joint
        (node_id, switch_id, switch_joint)
      select
        result_id as node_id,
        unnest(switch_ids) as switch_id,
        unnest(switch_joints) as switch_joint;
    else
      insert into layout.node_track_end
        (node_id, starting_location_track_id, ending_location_track_id)
        values
          (result_id, start_track, end_track);
    end if;
    return result_id;
  else -- Insert yielded nothing, so the node already exists
    select id from layout.node where key = new_key into result_id;
    return result_id;
  end if;
end;
$$ language plpgsql volatile;

-- TODO: This could be an immutable table where we just write new rows (new ids), instead of updating the existing ones
-- That way we need one less table, can have stronger references, writing becomes faster and reusing the edges (unique by combined segment data hash) is possible
create table layout.location_track_edge
(
  id            int primary key generated always as identity,
  start_node_id int                             not null references layout.node (id),
  end_node_id   int                             not null references layout.node (id),
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
