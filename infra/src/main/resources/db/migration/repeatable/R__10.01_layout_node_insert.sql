drop function if exists layout.get_or_insert_node;
create function layout.get_or_insert_node(
  switch_ids int[],
  switch_joint_numbers int[],
  switch_joint_roles common.switch_joint_role[],
  boundary_track_ids int[],
  boundary_types layout.boundary_type[]
) returns int as
$$
declare
  node_hash uuid;
  node_type layout.node_type :=
    case
      when cardinality(switch_ids) > 0 and cardinality(boundary_track_ids) = 0 then 'SWITCH'
      when cardinality(switch_ids) = 0 and cardinality(boundary_track_ids) > 0 then 'TRACK_BOUNDARY'
      else 'invalid' -- if neither applies, the type is incorrect -> break the variable assignment
    end;
  result_id int;
begin
  if node_type is null
  then
    raise exception
      'Node type is inconclusive due to conflicting content: switch_ids=% boundary_track_ids=% type=%', switch_ids, boundary_track_ids, node_type;
  end if;
  -- Node contents must be in a consistent order for correct port resolution
  if array [switch_ids[1], switch_joint_numbers[1], boundary_track_ids[1]] >
     array [switch_ids[2], switch_joint_numbers[2], boundary_track_ids[2]]
  then
    raise exception
      'Node ports in incorrect order or duplicated: switch_ids=[%] switch_joints=[%] boundary_track_ids[%]',
      switch_ids, switch_joint_numbers, boundary_track_ids;
  end if;

  drop table if exists port_tmp;
  create temporary table port_tmp as
  select *,
    layout.calculate_node_port_hash(
        node_type,
        switch_id,
        switch_joint_number,
        switch_joint_role,
        boundary_location_track_id,
        boundary_type
    ) as hash
    from (
      select
        unnest(array ['A', 'B'])::layout.node_port_type as port,
        unnest(switch_ids) as switch_id,
        unnest(switch_joint_numbers) as switch_joint_number,
        unnest(switch_joint_roles)::common.switch_joint_role as switch_joint_role,
        unnest(boundary_track_ids) as boundary_location_track_id,
        unnest(boundary_types)::layout.boundary_type as boundary_type
    ) tmp
    -- For switches, both ports must be linkable, even if B has no switch-joint
    where node_type = 'SWITCH' or boundary_location_track_id is not null;
  alter table port_tmp
    add primary key (port),
    alter column port set not null,
    alter column hash set not null;

  select
    layout.calculate_node_hash(
        (select hash from port_tmp where port = 'A'),
        (select hash from port_tmp where port = 'B')
    ),
    (select node_type from port_tmp where port = 'A')
    into node_hash, node_type;

  -- Try inserting node: if it already exists, the key will conflict
  insert into layout.node
    (type, hash)
    values
      (node_type, node_hash)
  on conflict (hash) do nothing
    returning id into result_id;

  -- If the row was inserted (no conflict) then the id is not null
  if result_id is not null then
    insert into layout.node_port
      (node_id, port, hash, switch_id, switch_joint_number, switch_joint_role, boundary_location_track_id,
       boundary_type)
    select
      result_id,
      port_tmp.port,
      port_tmp.hash,
      port_tmp.switch_id,
      port_tmp.switch_joint_number,
      port_tmp.switch_joint_role,
      port_tmp.boundary_location_track_id,
      port_tmp.boundary_type
      from port_tmp;
    drop table port_tmp;
    return result_id;
  else -- Insert yielded nothing, so the node already exists
    select id from layout.node where hash = node_hash into result_id;
    drop table port_tmp;
    return result_id;
  end if;
end;
$$ language plpgsql volatile;
