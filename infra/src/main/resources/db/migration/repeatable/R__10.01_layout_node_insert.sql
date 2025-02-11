drop function if exists layout.get_or_insert_node;
create function layout.get_or_insert_node(
  switch_in_id int,
  switch_in_joint_number int,
  switch_in_joint_role common.switch_joint_role,
  switch_out_id int,
  switch_out_joint_number int,
  switch_out_joint_role common.switch_joint_role,
  start_track_id int,
  end_track_id int
) returns int as
$$
declare
  new_hash         uuid := (
    select layout.calculate_node_hash(
        switch_in_id,
        switch_in_joint_number,
        switch_in_joint_role,
        switch_out_id,
        switch_out_joint_number,
        switch_out_joint_role,
        start_track_id,
        end_track_id
    )
  );
  declare result_id int;
begin

  -- Try inserting node: if it already exists, the key will conflict
  insert into layout.node
    (
      hash,
      switch_in_id,
      switch_in_joint_number,
      switch_in_joint_role,
      switch_out_id,
      switch_out_joint_number,
      switch_out_joint_role,
      starting_location_track_id,
      ending_location_track_id
    )
    values
      (
        new_hash,
        switch_in_id,
        switch_in_joint_number,
        switch_in_joint_role,
        switch_out_id,
        switch_out_joint_number,
        switch_out_joint_role,
        start_track_id,
        end_track_id
      )
  on conflict (hash) do nothing
    returning id into result_id;

  -- If the row was inserted (no conflict) then the id is not null
  if result_id is not null then
    return result_id;
  else -- Insert yielded nothing, so the node already exists
    select id from layout.node where hash = new_hash into result_id;
    return result_id;
  end if;
end;
$$ language plpgsql volatile;
