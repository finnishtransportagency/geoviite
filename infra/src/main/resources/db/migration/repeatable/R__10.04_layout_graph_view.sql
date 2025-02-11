drop view if exists layout.location_track_version_switch_view;
drop view if exists layout.location_track_version_node_view;

create view layout.location_track_version_node_view as
select distinct on (node_id, location_track_id, location_track_layout_context_id, location_track_version)
  *
  from (
    select
      ltve.location_track_id,
      ltve.location_track_layout_context_id,
      ltve.location_track_version,
      unnest(array[edge.start_node_id, edge.end_node_id]) as node_id,
      unnest(array[2*ltve.edge_index, 2*ltve.edge_index + 1]) as node_sort
      from layout.location_track_version_edge ltve inner join layout.edge edge on ltve.edge_id = edge.id
      where edge.start_node_id is not null or end_node_id is not null
  ) tmp
  where node_id is not null;

create view layout.location_track_version_switch_view as
select distinct on (switch_id, location_track_id, location_track_layout_context_id, location_track_version)
  *
  from (
    select
      ltvn.location_track_id,
      ltvn.location_track_layout_context_id,
      ltvn.location_track_version,
      unnest(array[node.switch_1_id, node.switch_2_id]) as switch_id,
      unnest(array[2*ltvn.node_sort, 2*ltvn.node_sort + 1]) as switch_sort
      from layout.location_track_version_node_view ltvn
        inner join layout.node node on ltvn.node_id = node.id
      where node.switch_1_id is not null or node.switch_2_id is not null
  ) tmp
  where switch_id is not null;

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


drop view if exists switch_location_track_version_view;
create view switch_location_track_version_view as
select distinct
  sv.id as switch_id,
  ltve.location_track_id,
  ltve.location_track_layout_context_id,
  ltve.location_track_version
  from layout.switch_version sv
    inner join layout.node on sv.id in (node.switch_1_id, node.switch_2_id)
    inner join layout.edge on node.id in (edge.start_node_id, edge.end_node_id)
    inner join layout.location_track_version_edge ltve on edge.id = ltve.edge_id;

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
