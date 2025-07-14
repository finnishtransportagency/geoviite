drop view if exists layout.location_track_version_switch_view;
create view layout.location_track_version_switch_view as
select
  location_track_id,
  location_track_layout_context_id,
  location_track_version,
  switch_id,
  switch_joint_number,
  switch_joint_role,
  -- Generate a sortable ordering for the links, based on how they connect to the edge. Each node port for a given
  -- track version only appears once.
  case
    when node_id = start_node_id and port <> start_node_port then 4 * edge_index
    when node_id = start_node_id and port = start_node_port then 4 * edge_index + 1
    when node_id = end_node_id and port = end_node_port then 4 * edge_index + 2
    when node_id = end_node_id and port <> end_node_port then 4 * edge_index + 3
  end as switch_sort,
  (edge_index = 0 and node_id = start_node_id and port <> start_node_port)
    or (edge_index = (edge_count - 1) and node_id = end_node_id and port <> end_node_port
    ) as is_outer_link
  from (
    -- PostgreSQL is extremely finicky about optimization here. Have to separate the query for the track's start node
    -- (edge_index = 0 and np.node_id = edge.start_node_id) from the rest (np.node_id = edge.end_node_id) entirely
    -- and only union them together at the end, for all use cases to go fast.
    (
      select
        ltv.edge_count,
        ltve.location_track_id,
        ltve.location_track_version,
        ltve.location_track_layout_context_id,
        ltve.edge_index,
        np.switch_id,
        np.switch_joint_number,
        np.switch_joint_role,
        np.node_id,
        edge.start_node_id,
        edge.start_node_port,
        edge.end_node_id,
        edge.end_node_port,
        np.port
        from layout.node_port np
          inner join layout.edge edge on np.node_id = edge.start_node_id
          inner join layout.location_track_version_edge ltve on ltve.edge_id = edge.id
          inner join layout.location_track_version ltv
                     on ltve.location_track_id = ltv.id
                       and ltve.location_track_layout_context_id = ltv.layout_context_id
                       and ltve.location_track_version = ltv.version
        where np.switch_id is not null and ltve.edge_index = 0
    )
    union all
    (
      select
        ltv.edge_count,
        ltve.location_track_id,
        ltve.location_track_version,
        ltve.location_track_layout_context_id,
        ltve.edge_index,
        np.switch_id,
        np.switch_joint_number,
        np.switch_joint_role,
        np.node_id,
        edge.start_node_id,
        edge.start_node_port,
        edge.end_node_id,
        edge.end_node_port,
        np.port
        from layout.node_port as np
          inner join layout.edge edge on np.node_id = edge.end_node_id
          inner join layout.location_track_version_edge ltve on ltve.edge_id = edge.id
          inner join layout.location_track_version ltv
                     on ltve.location_track_id = ltv.id
                       and ltve.location_track_layout_context_id = ltv.layout_context_id
                       and ltve.location_track_version = ltv.version
        where np.switch_id is not null
    )
  ) ns;

drop view if exists layout.edge_ends_view;
create view layout.edge_ends_view as
select
  edge.id,
  postgis.st_startpoint(first_geom.geometry) as start_point,
  postgis.st_endpoint(last_geom.geometry) as end_point
  from layout.edge
    inner join layout.edge_segment first_segment on edge.id = first_segment.edge_id and first_segment.segment_index = 0
    inner join layout.segment_geometry first_geom on first_segment.geometry_id = first_geom.id
    inner join layout.edge_segment last_segment
               on edge.id = last_segment.edge_id and last_segment.segment_index = edge.segment_count - 1
    inner join layout.segment_geometry last_geom on last_segment.geometry_id = last_geom.id;

drop view if exists layout.location_track_version_ends_view;
create view layout.location_track_version_ends_view as
select
  ltv.id,
  ltv.layout_context_id,
  ltv.version,
  postgis.st_startpoint(first_geom.geometry) as start_point,
  postgis.st_endpoint(last_geom.geometry) as end_point,
  first_ltve.edge_index as first_edge_index,
  last_ltve.edge_index as last_edge_index,
  first_ltve.edge_id as first_edge_id,
  last_ltve.edge_id as last_edge_id,
  first_segment.segment_index as first_segment_index,
  last_segment.segment_index as last_segment_index
  from layout.location_track_version ltv
    left join layout.location_track_version_edge first_ltve
              on ltv.id = first_ltve.location_track_id
                and ltv.layout_context_id = first_ltve.location_track_layout_context_id
                and ltv.version = first_ltve.location_track_version
                and first_ltve.edge_index = 0
    left join layout.edge_segment first_segment
              on first_ltve.edge_id = first_segment.edge_id and first_segment.segment_index = 0
    left join layout.segment_geometry first_geom on first_segment.geometry_id = first_geom.id
    left join layout.location_track_version_edge last_ltve
              on ltv.id = last_ltve.location_track_id
                and ltv.layout_context_id = last_ltve.location_track_layout_context_id
                and ltv.version = last_ltve.location_track_version
                and last_ltve.edge_index = ltv.edge_count - 1
    left join layout.edge last_edge on last_edge.id = last_ltve.edge_id
    left join layout.edge_segment last_segment
              on last_edge.id = last_segment.edge_id and last_segment.segment_index = last_edge.segment_count - 1
    left join layout.segment_geometry last_geom on last_segment.geometry_id = last_geom.id;
