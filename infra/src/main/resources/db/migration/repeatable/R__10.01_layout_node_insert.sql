drop function if exists layout.get_or_insert_node;
create function layout.get_or_insert_node(
  switch_1_id int,
  switch_1_joint_number int,
  switch_1_joint_role common.switch_joint_role,
  switch_2_id int,
  switch_2_joint_number int,
  switch_2_joint_role common.switch_joint_role,
  start_track_id int,
  end_track_id int
) returns int as
$$
declare
  new_hash         uuid := (
    select layout.calculate_node_hash(
        switch_1_id,
        switch_1_joint_number,
        switch_1_joint_role,
        switch_2_id,
        switch_2_joint_number,
        switch_2_joint_role,
        start_track_id,
        end_track_id
    )
  );
  declare result_id int;
begin
  -- Node switches must be in a consistent order
  if array [switch_1_id, switch_1_joint_number] > array [switch_2_id, switch_2_joint_number] then
    raise exception 'Node switches in incorrect order: switch1=[%, %] switch2=[%, %]', switch_1_id, switch_1_joint_number, switch_2_id, switch_2_joint_number;
  end if;

  -- Try inserting node: if it already exists, the key will conflict
  insert into layout.node
    (
      hash,
      switch_1_id,
      switch_1_joint_number,
      switch_1_joint_role,
      switch_2_id,
      switch_2_joint_number,
      switch_2_joint_role,
      starting_location_track_id,
      ending_location_track_id
    )
    values
      (
        new_hash,
        switch_1_id,
        switch_1_joint_number,
        switch_1_joint_role,
        switch_2_id,
        switch_2_joint_number,
        switch_2_joint_role,
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
