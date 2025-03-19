drop view if exists layout.location_track_version_switch_view;
drop view if exists layout.location_track_version_node_view;

-- create view layout.location_track_version_node_view as
-- select distinct on (location_track_id, location_track_layout_context_id, location_track_version, node_id, node_port)
--   *
--   from (
--     select
--       ltve.location_track_id,
--       ltve.location_track_layout_context_id,
--       ltve.location_track_version,
--       unnest(array[edge.start_node_id, edge.end_node_id]) as node_id,
--       unnest(array[edge.start_node_port, edge.end_node_port]) as node_port,
--       unnest(array[2*ltve.edge_index, 2*ltve.edge_index + 1]) as node_sort,
--
--       from layout.location_track_version_edge ltve inner join layout.edge edge on ltve.edge_id = edge.id
--   ) tmp
--   where node_id is not null;

-- create view layout.location_track_version_switch_view_orig as
-- select
--   distinct on (
--     location_track_id,
--     location_track_layout_context_id,
--     location_track_version,
--     switch_id,
--     switch_joint_number
--     )
--   ltve.location_track_id,
--   ltve.location_track_layout_context_id,
--   ltve.location_track_version,
--   np.switch_id,
--   np.switch_joint_number,
--   np.switch_joint_role,
--   -- Generate a sortable ordering for the links, based on how they connect to the edge
--   -- Note: the following edge will likely have share a node (and hence 2 links) so the distinct above is required
--   -- However, it does not matter which edge's version of the ordering number gets picked, it's still in the same place
--   case
--     when np.node_id = edge.start_node_id and np.port <> edge.start_node_port then 4 * ltve.edge_index
--     when np.node_id = edge.start_node_id and np.port = edge.start_node_port then 4 * ltve.edge_index + 1
--     when np.node_id = edge.end_node_id and np.port = edge.end_node_port then 4 * ltve.edge_index + 2
--     when np.node_id = edge.end_node_id and np.port <> edge.end_node_port then 4 * ltve.edge_index + 3
--   end as switch_sort
--   from layout.location_track_version_edge ltve
--     inner join layout.edge edge on ltve.edge_id = edge.id
--     inner join layout.node_port np on np.node_id in (edge.start_node_id, edge.end_node_id)
--   where switch_id is not null;
-- --     and location_track_id = 1923
-- --     and location_track_layout_context_id = 'main_draft'
-- --     and location_track_version = 21;
--
--
-- create view layout.location_track_version_switch_view_rev as
-- with
--   switch_edge as (
--     select node_port.switch_id, edge.id as edge_id
--       from layout.node_port
--         inner join layout.edge on node_port.node_id = edge.end_node_id or node_port.port = edge.end_node_port
--       where node_port.switch_id is not null
--   )
-- select distinct
--   ltve.location_track_id,
--   ltve.location_track_layout_context_id,
--   ltve.location_track_version,
--   np.switch_id
-- from layout.location_track_version_edge ltve
--   inner join layout.edge on ltve.edge_id = edge.id
--   inner join layout.node_port np on np.node_id = edge.end_node_id or np.port = edge.end_node_port
-- where np.switch_id is not null;
-- ;

create view layout.location_track_version_switch_view as
select
  distinct
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
    or (ltve.edge_index = (ltv.edge_count-1) and np.node_id = edge.end_node_id and np.port <> edge.end_node_port
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
-- --
-- --     and location_track_layout_context_id = 'main_draft'
-- --     and location_track_version = 21;
--
-- create view layout.location_track_version_switch_view as
-- select
-- --   distinct on (
-- --     location_track_id,
-- --     location_track_layout_context_id,
-- --     location_track_version,
-- --     switch_id,
-- --     switch_joint_number
-- --     )
--   ltve.location_track_id,
--   ltve.location_track_layout_context_id,
--   ltve.location_track_version,
--   np.switch_id,
--   np.switch_joint_number,
--   np.switch_joint_role,
--   -- Generate a sortable ordering for the links, based on how they connect to the edge
--   -- Note: the following edge will likely have share a node (and hence 2 links) so the distinct above is required
--   -- However, it does not matter which edge's version of the ordering number gets picked, it's still in the same place
--   case
--     when np.node_id = edge.start_node_id and np.port <> edge.start_node_port then 4 * ltve.edge_index
--     when np.node_id = edge.start_node_id and np.port = edge.start_node_port then 4 * ltve.edge_index + 1
--     when np.node_id = edge.end_node_id and np.port = edge.end_node_port then 4 * ltve.edge_index + 2
--     when np.node_id = edge.end_node_id and np.port <> edge.end_node_port then 4 * ltve.edge_index + 3
--   end as switch_sort
--   from layout.location_track_version_edge ltve
--     inner join layout.edge edge on ltve.edge_id = edge.id
--     inner join layout.node_port np on np.node_id in (edge.start_node_id, edge.end_node_id)
--   where switch_id is not null;
-- --     and location_track_id = 1923
-- --     and location_track_layout_context_id = 'main_draft'
-- --     and location_track_version = 21;
--
-- select count(*) from layout.location_track_version_switch_view_orig;
-- select count(*) from layout.location_track_version_switch_view;
--
-- select distinct on (ltv_s.location_track_id, ltv_s.switch_id)
--   ltv_s.location_track_id,
--   ltv_s.location_track_layout_context_id,
--   ltv_s.location_track_version,
--   ltv_s.switch_id,
--   lt.id
-- --   from layout.location_track_version_switch_view ltv_s
--   from layout.location_track_version_switch_view_orig ltv_s
--     inner join layout.location_track lt
-- --   left join layout.location_track_in_layout_context(:publication_state::layout.publication_state, :design_id) lt
--                on ltv_s.location_track_id = lt.id
--                  and ltv_s.location_track_layout_context_id = lt.layout_context_id
--                  and ltv_s.location_track_version = lt.version;

-- create view layout.location_track_version_switch_view as
-- select distinct on (location_track_id, location_track_layout_context_id, location_track_version, switch_id)
--   *
--   from (
--     select distinct
--       ltvn.location_track_id,
--       ltvn.location_track_layout_context_id,
--       ltvn.location_track_version,
--       np.switch_id,
--       -- TODO: If included, also include in the distinct clause above
-- --       np.switch_joint_number,
-- --       np.switch_joint_role,
--       case
--         when node_port = np.port then node_sort
--         when
--       end as switch_sort
-- --       unnest(array[2*ltvn.node_sort, 2*ltvn.node_sort + 1]) as switch_sort
--       from layout.location_track_version_node_view ltvn
--         inner join layout.node_port np on ltvn.node_id = np.node_id-- and ltvn.node_port = np.port
--       where np.switch_id is not null
--   ) tmp
--   where switch_id is not null;

-- select array_agg(switch_id order by switch_sort)
--   from layout.location_track_version_switch_view
--   where location_track_id = 14
--     and location_track_layout_context_id = 'main_official'
--     and location_track_version = 2;


-- select distinct location_track_id, location_track_layout_context_id, location_track_version
--   from layout.location_track_version_switch_view
--   where switch_id = 14;
-- --     and location_track_layout_context_id = 'main_official';


-- select *
--   from switch_location_track_view s_ltv
--     inner join layout.location_track lt on s_ltv.location_track_id = lt.id and s_ltv.location_track_layout_context_id = lt.layout_context_id and s_ltv.location_track_version = lt.version
-- where switch_id = 14;


-- drop view if exists switch_location_track_version_view;
-- create view switch_location_track_version_view as
-- select distinct
--   sv.id as switch_id,
--   ltve.location_track_id,
--   ltve.location_track_layout_context_id,
--   ltve.location_track_version
--   from layout.switch_version sv
--     inner join layout.node on sv.id in (node.switch_1_id, node.switch_2_id)
--     inner join layout.edge on node.id in (edge.start_node_id, edge.end_node_id)
--     inner join layout.location_track_version_edge ltve on edge.id = ltve.edge_id;

-- select * from switch_location_track_version_view where switch_id = 14 and location_track_layout_context_id = 'main_official';
-- select * from switch_location_track_version_view where location_track_id = 134 and location_track_layout_context_id = 'main_official';
--
-- select * from switch_location_track_version_view_2 where switch_id = 14 and location_track_layout_context_id = 'main_official';
-- select * from switch_location_track_version_view_2 where location_track_id = 134 and location_track_layout_context_id = 'main_official';

-- select distinct
--   ltve.location_track_id,
--   ltve.location_track_layout_context_id,
--   ltve.location_track_version
--   from layout.node
--     inner join layout.edge on node.id in (edge.start_node_id, edge.end_node_id)
--     inner join layout.location_track_version_edge ltve on edge.id = ltve.edge_id
--   where
--     14 in (switch_1_id, switch_2_id)
--     and ltve.location_track_layout_context_id = 'main_official';
--
-- select distinct
--   ltve.location_track_id,
--   ltve.location_track_layout_context_id,
--   ltve.location_track_version
-- from layout.node
--   inner join layout.edge on node.id in (edge.start_node_id, edge.end_node_id)
--   inner join layout.location_track_version_edge ltve on edge.id = ltve.edge_id
-- where 14 in (switch_1_id, switch_2_id)
--   and ltve.location_track_layout_context_id = 'main_official';

drop view if exists layout.edge_ends_view;
create view layout.edge_ends_view as
  select
    edge.id,
    postgis.st_startpoint(first_geom.geometry) as start_point,
    postgis.st_endpoint(last_geom.geometry) as end_point
    from layout.edge
      inner join layout.edge_segment first_segment on edge.id = first_segment.edge_id and first_segment.segment_index = 0
      inner join layout.segment_geometry first_geom on first_segment.geometry_id = first_geom.id
      inner join layout.edge_segment last_segment on edge.id = last_segment.edge_id and last_segment.segment_index = edge.segment_count - 1
      inner join layout.segment_geometry last_geom on last_segment.geometry_id = last_geom.id;

drop view if exists layout.location_track_version_ends_view;
create view layout.location_track_version_ends_view as
  select
    ltv.id,
    ltv.layout_context_id,
    ltv.version,
    postgis.st_startpoint(first_geom.geometry) as start_point,
    postgis.st_endpoint(last_geom.geometry) as end_point,
    first_ltve.edge_index first_edge_index,
    last_ltve.edge_index last_edge_index,
    first_ltve.edge_id first_edge_id,
    last_ltve.edge_id last_edge_id,
    first_segment.segment_index first_segment_index,
    last_segment.segment_index last_segment_index
    from layout.location_track_version ltv
      left join layout.location_track_version_edge first_ltve
                 on ltv.id = first_ltve.location_track_id
                   and ltv.layout_context_id = first_ltve.location_track_layout_context_id
                   and ltv.version = first_ltve.location_track_version
                   and first_ltve.edge_index = 0
      left join layout.edge_segment first_segment on first_ltve.edge_id = first_segment.edge_id and first_segment.segment_index = 0
      left join layout.segment_geometry first_geom on first_segment.geometry_id = first_geom.id
      left join layout.location_track_version_edge last_ltve
                 on ltv.id = last_ltve.location_track_id
                   and ltv.layout_context_id = last_ltve.location_track_layout_context_id
                   and ltv.version = last_ltve.location_track_version
                   and last_ltve.edge_index = ltv.edge_count - 1
      left join layout.edge last_edge on last_edge.id = last_ltve.edge_id
      left join layout.edge_segment last_segment on last_edge.id = last_segment.edge_id and last_segment.segment_index = last_edge.segment_count - 1
      left join layout.segment_geometry last_geom on last_segment.geometry_id = last_geom.id;
