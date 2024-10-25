create or replace function layout.get_or_insert_node(
  switch_in int,
  switch_in_joint_number int,
  switch_in_joint_type int,
  switch_out int,
  switch_out_joint_number int,
  switch_out_joint_type int,
  start_track int,
  end_track int
) returns int as
$$
declare
  new_key         uuid := (
    select layout.calculate_node_hash(
        switch_in,
        switch_in_joint_number,
        switch_in_joint_type,
        switch_out,
        switch_out_joint_number,
        switch_out_joint_type,
        start_track,
        end_track
    )
  );
  declare result_id int;
begin

  -- Try inserting node: if it already exists, the key will conflict
  insert into layout.node
    (
      key,
      switch_in_id,
      switch_in_joint_number,
      switch_in_joint_type,
      switch_out_id,
      switch_out_joint_number,
      switch_out_joint_type,
      starting_location_track_id,
      ending_location_track_id
    )
    values
      (
        key,
        switch_in,
        switch_in_joint_number,
        switch_in_joint_type,
        switch_out,
        switch_out_joint_number,
        switch_out_joint_type,
        start_track,
        end_track
      )
  on conflict (key) do nothing
    returning id into result_id;

  -- If the row was inserted (no conflict) then the id is not null
  if result_id is not null then
    return result_id;
  else -- Insert yielded nothing, so the node already exists
    select id from layout.node where key = new_key into result_id;
    return result_id;
  end if;
end;
$$ language plpgsql volatile;
