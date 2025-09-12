drop function if exists layout.get_or_insert_node;
drop function if exists layout.get_or_insert_nodes;
drop type if exists layout.node_insert_result;

create type layout.node_insert_result as
(
  insert_node_id int,
  node_id        int
);

create function layout.get_or_insert_nodes(
  insert_node_ids int[],
  ports layout.node_port_type[],
  switch_ids int[],
  switch_joint_numbers int[],
  switch_joint_roles common.switch_joint_role[],
  boundary_track_ids int[],
  boundary_types layout.boundary_type[]
) returns setof layout.node_insert_result as
$$
with port as (
  select
    unnest(insert_node_ids) as insert_node_id,
    unnest(ports)::layout.node_port_type as port_type,
    unnest(switch_ids) as switch_id,
    unnest(switch_joint_numbers) as switch_joint_number,
    unnest(switch_joint_roles)::common.switch_joint_role as switch_joint_role,
    unnest(boundary_track_ids) as boundary_track_id,
    unnest(boundary_types)::layout.boundary_type as boundary_type
),
  -- (insert_node_id, node_type), row per distinct insert_node_id
  node_type as (
    select
      insert_node_id,
      case
        when
              count(*) filter (where switch_id is not null) > 0 and
              count(*) filter (where boundary_track_id is not null) = 0 then 'SWITCH'
        when
              count(*) filter (where switch_id is not null) = 0 and
              count(*) filter (where boundary_track_id is not null) > 0 then 'TRACK_BOUNDARY'
      end::layout.node_type as type
      from port
      group by insert_node_id
  ),
  -- (insert_node_id, port_type, hash), row per distinct (insert_node_id, port_type)
  port_hash as (
    select
      insert_node_id,
      port.port_type,
      layout.calculate_node_port_hash(
          node_type.type,
          switch_id,
          switch_joint_number,
          switch_joint_role,
          boundary_track_id,
          boundary_type
      ) as hash
      from port
        join node_type using (insert_node_id)
  ),
  -- (insert_node_id, hash), row per distinct insert_node_id
  node_hash as (
    select insert_node_id, layout.calculate_node_hash(port_a.hash, port_b.hash) as hash
      from node_type
        join (
        select *
          from port_hash
          where port_type = 'A'
      ) port_a using (insert_node_id)
        left join (
        select *
          from port_hash
          where port_type = 'B'
      ) port_b using (insert_node_id)
  ),
  -- (insert_node_id, hash), row per distinct hash
  distinct_node_hash as (
    select distinct on (hash) insert_node_id, hash
      from node_hash
  ),
  -- (id, hash), row per inserted node
  inserted_node as (
    insert into layout.node (type, hash)
      (
        select type, hash
          from node_type
            join distinct_node_hash using (insert_node_id)
      )
      on conflict do nothing
      returning id, hash
  ),
  inserted_port as (
    insert into layout.node_port
      (node_id, port, hash,
       switch_id, switch_joint_number, switch_joint_role,
       boundary_location_track_id, boundary_type)
      (
        select
          inserted_node.id,
          port.port_type,
          port_hash.hash,
          switch_id,
          switch_joint_number,
          switch_joint_role,
          boundary_track_id,
          boundary_type
          from inserted_node
            join distinct_node_hash using (hash)
            join node_type using (insert_node_id)
            join port_hash using (insert_node_id)
            join port using (insert_node_id, port_type)
          where node_type.type = 'SWITCH' or boundary_track_id is not null
      )
  )
select insert_node_id, node_id.id as node_id
  from node_hash
    join (
    select hash, id
      from inserted_node
    union all
    select hash, id
      from layout.node
  ) node_id using (hash)
$$ language sql volatile;
