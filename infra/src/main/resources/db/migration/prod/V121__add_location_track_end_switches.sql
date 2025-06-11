alter table layout.location_track
  disable trigger version_update_trigger,
  disable trigger version_row_trigger;

alter table layout.location_track_version
  add column start_switch_id int null,
  add column end_switch_id   int null;

alter table layout.location_track
  add column start_switch_id int null references layout.switch_id (id),
  add column end_switch_id   int null references layout.switch_id (id);

update layout.location_track_version ltv
set
  start_switch_id = (
    select
      case
        when node_in.switch_joint_role = 'MAIN' then node_in.switch_id
        when node_out.switch_joint_role = 'MAIN' then node_out.switch_id
        else coalesce(node_in.switch_id, node_out.switch_id)
      end as start_switch_id
      from layout.location_track_version_edge first_ltve
        inner join layout.edge on edge.id = first_ltve.edge_id
        left join layout.node_port node_in
                  on edge.start_node_id = node_in.node_id
                    and edge.start_node_port = node_in.port
        left join layout.node_port node_out
                  on edge.start_node_id = node_out.node_id
                    and edge.start_node_port <> node_out.port
      where ltv.id = first_ltve.location_track_id
        and ltv.layout_context_id = first_ltve.location_track_layout_context_id
        and ltv.version = first_ltve.location_track_version
        and first_ltve.edge_index = 0
  ),
  end_switch_id = (
    select
      case
        when node_in.switch_joint_role = 'MAIN' then node_in.switch_id
        when node_out.switch_joint_role = 'MAIN' then node_out.switch_id
        else coalesce(node_in.switch_id, node_out.switch_id)
      end as end_switch_id
      from layout.location_track_version_edge last_ltve
        left join layout.edge on edge.id = last_ltve.edge_id
        left join layout.node_port node_in
                  on edge.end_node_id = node_in.node_id
                    and edge.end_node_port = node_in.port
        left join layout.node_port node_out
                  on edge.end_node_id = node_out.node_id
                    and edge.end_node_port <> node_out.port
      where ltv.id = last_ltve.location_track_id
        and ltv.layout_context_id = last_ltve.location_track_layout_context_id
        and ltv.version = last_ltve.location_track_version
        and last_ltve.edge_index = ltv.edge_count - 1
  )
  where true;

update layout.location_track lt
set start_switch_id = ltv.start_switch_id, end_switch_id = ltv.end_switch_id
  from layout.location_track_version ltv
  where lt.id = ltv.id and lt.layout_context_id = ltv.layout_context_id and lt.version = ltv.version;

alter table layout.location_track
  enable trigger version_update_trigger,
  enable trigger version_row_trigger;
