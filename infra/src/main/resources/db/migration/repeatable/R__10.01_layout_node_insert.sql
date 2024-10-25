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
    (key, starting_location_track_id, ending_location_track_id)
    values
      (new_key, start_track, end_track)
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
    end if;
    return result_id;
  else -- Insert yielded nothing, so the node already exists
    select id from layout.node where key = new_key into result_id;
    return result_id;
  end if;
end;
$$ language plpgsql volatile;
