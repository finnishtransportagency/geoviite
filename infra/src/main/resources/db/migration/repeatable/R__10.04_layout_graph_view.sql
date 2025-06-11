drop view if exists layout.location_track_version_switch_view;
create view layout.location_track_version_switch_view as
select distinct
  ltve.location_track_id,
  ltve.location_track_layout_context_id,
  ltve.location_track_version,
  np.switch_id,
  np.switch_joint_number,
  np.switch_joint_role,
  -- Generate a sortable ordering for the links, based on how they connect to the edge
  -- Note: the following edge will likely have share a node (and hence 2 links) so the distinct above is required
  -- However, it does not matter which edge's version of the ordering number gets picked, it's still in the same place
  case
    when np.node_id = edge.start_node_id and np.port <> edge.start_node_port then 4 * ltve.edge_index
    when np.node_id = edge.start_node_id and np.port = edge.start_node_port then 4 * ltve.edge_index + 1
    when np.node_id = edge.end_node_id and np.port = edge.end_node_port then 4 * ltve.edge_index + 2
    when np.node_id = edge.end_node_id and np.port <> edge.end_node_port then 4 * ltve.edge_index + 3
  end as switch_sort,
  (ltve.edge_index = 0 and np.node_id = edge.start_node_id and np.port <> edge.start_node_port)
    or (ltve.edge_index = (ltv.edge_count - 1) and np.node_id = edge.end_node_id and np.port <> edge.end_node_port
    ) as is_outer_link
  from layout.node_port np
    inner join layout.edge edge on np.node_id in (edge.start_node_id, edge.end_node_id)
    inner join layout.location_track_version_edge ltve on ltve.edge_id = edge.id
    inner join layout.location_track_version ltv
               on ltve.location_track_id = ltv.id
                 and ltve.location_track_layout_context_id = ltv.layout_context_id
                 and ltve.location_track_version = ltv.version
  where np.switch_id is not null
    and (np.node_id = edge.end_node_id or ltve.edge_index = 0);

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
