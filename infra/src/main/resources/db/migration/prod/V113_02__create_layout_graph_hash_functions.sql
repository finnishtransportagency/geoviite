create function layout.calculate_node_port_hash(
  node_type layout.node_type,
  switch_id                  int,
  switch_joint_number        int,
  switch_joint_role          common.switch_joint_role,
  boundary_location_track_id int,
  boundary_type              layout.boundary_type
) returns uuid
  language sql as
$$
select
  md5(
      row(node_type, switch_id, switch_joint_number, switch_joint_role, boundary_location_track_id, boundary_type)::text
  )::uuid
$$ immutable;

create function layout.calculate_node_hash(
  port_a_hash uuid,
  port_b_hash uuid
) returns uuid
  language sql as
$$
select md5(row (port_a_hash, port_b_hash)::text)::uuid
$$ immutable;

create function layout.calculate_segment_hash(
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

create function layout.calculate_edge_hash(
  start_node_id int,
  start_node_port layout.node_port_type,
  end_node_id int,
  end_node_port layout.node_port_type,
  segment_hashes uuid[]
) returns uuid
  language sql as
$$
select md5(row (start_node_id, start_node_port, end_node_id, end_node_port, segment_hashes)::text)::uuid
$$ immutable;
