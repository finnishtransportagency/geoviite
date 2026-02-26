-- drop table if exists fixed_edges;
-- Table to collect the new edge data
create temporary table fixed_edges as (
  with edges_missing_inner as (
    select
      edge.*,
      start_np_in.switch_id as start_in_switch_id,
      start_np_in.switch_joint_number as start_in_joint,
      end_np_in.switch_id as end_in_switch_id,
      end_np_in.switch_joint_number as end_in_joint,
      (start_np_in.switch_id is not null) as start_ok,
      (end_np_in.switch_id is not null) as end_ok
      from layout.edge
        left join layout.node_port start_np_in
                  on start_np_in.node_id = edge.start_node_id and start_np_in.port = edge.start_node_port
        left join layout.node_port start_np_out
                  on start_np_out.node_id = edge.start_node_id and start_np_out.port != edge.start_node_port
        left join layout.node_port end_np_in
                  on end_np_in.node_id = edge.end_node_id and end_np_in.port = edge.end_node_port
        left join layout.node_port end_np_out
                  on end_np_out.node_id = edge.end_node_id and end_np_out.port != edge.end_node_port
    -- Edges that are connected to the same switch from both ends...
      where
        coalesce(start_np_in.switch_id, start_np_out.switch_id) = coalesce(end_np_in.switch_id, end_np_out.switch_id)
        -- ... but at least one end is not linked as inner
        and (start_np_in.switch_id is null or end_np_in.switch_id is null)
  ),
    edges_with_flipped_ports as (
      select
        original.id as original_id,
        original.start_node_id,
        -- Flip the node ports without other changes
        -- Due to the select logic above, this will only flip single-switch node connections that are connected from
        -- the outside to instead connect from the inner side
        case
          when original.start_ok then original.start_node_port
          when original.start_node_port = 'A' then 'B'
          when original.start_node_port = 'B' then 'A'
        end as start_node_port,
        original.end_node_id,
        case
          when original.end_ok then original.end_node_port
          when original.end_node_port = 'A' then 'B'
          when original.end_node_port = 'B' then 'A'
        end as end_node_port,
        original.bounding_box,
        original.start_location,
        original.end_location,
        original.segment_count,
        original.length,
        -- The segment hashes are the same as the original edge had, but we need to collect them from the segments
        (
          select array_agg(hash order by s.segment_index)
            from layout.edge_segment s
            where s.edge_id = original.id
        ) as segment_hashes
        from edges_missing_inner original
    ),
    edges_with_new_hash as (
      select
        flipped.*,
        -- The new edge hash with flipped ports
        layout.calculate_edge_hash(
            flipped.start_node_id,
            flipped.start_node_port,
            flipped.end_node_id,
            flipped.end_node_port,
            flipped.segment_hashes
        ) as new_edge_hash
        from edges_with_flipped_ports flipped
    )
  select
    fixed.*,
    -- Also pull out any existing edge with the same hash (== identical content)
    -- We must reuse that instead of creating a new one
    (
      select id
        from layout.edge
        where hash = fixed.new_edge_hash
    ) as existing_fixed_id
    from edges_with_new_hash fixed
);

-- drop table if exists replacement_edges;
create temporary table replacement_edges as (
  -- Insert the new edges that don't already exist
  with inserted as (
    insert into layout.edge
      (start_node_id, start_node_port, end_node_id, end_node_port, bounding_box, start_location, end_location,
       segment_count, length, hash)
      select
        start_node_id,
        start_node_port,
        end_node_id,
        end_node_port,
        bounding_box,
        start_location,
        end_location,
        segment_count,
        length,
        new_edge_hash
        from fixed_edges
        where existing_fixed_id is null
      returning id, hash
  )
  -- Collect all the ids for mapping the old edges for new ones
  select
    fixed.original_id,
    fixed.new_edge_hash,
    fixed.existing_fixed_id,
    inserted.id as inserted_id
    from fixed_edges fixed
      left join inserted on inserted.hash = fixed.new_edge_hash
);

-- Also insert new edge segments for the new edges. We could reuse the old ones, but this makes it clearer when we get to the cleanup phase
insert into layout.edge_segment(edge_id, segment_index, geometry_alignment_id, geometry_element_index, start_m,
                                source_start_m, source, geometry_id, hash)
select
  inserted_id,
  segment_index,
  geometry_alignment_id,
  geometry_element_index,
  start_m,
  source_start_m,
  source,
  geometry_id,
  hash
  from layout.edge_segment
    inner join replacement_edges replacement on replacement.original_id = edge_segment.edge_id
  where replacement.inserted_id is not null;

-- Update all location tracks to use the new edges: here it can be either the reused or an existing replacement
update layout.location_track_version_edge ltve
set edge_id = coalesce(replacement.existing_fixed_id, replacement.inserted_id)
  from replacement_edges replacement
  where ltve.edge_id = replacement.original_id;

-- Now, no-one should be using these, so it's safe to delete the old faulty edges with their segments
delete
  from layout.edge_segment
  where edge_id in (
    select original_id
      from replacement_edges
  );
delete
  from layout.edge
  where id in (
    select original_id
      from replacement_edges
  );
