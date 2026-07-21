-- GVT-3030: Backfill geometry_switch_id on layout.switch rows that are NULL due to a regression
-- introduced in commit acb4b1161 (GVT-3105, 2025-06-09). The write path in saveSwitchLinking
-- stopped forwarding geometrySwitchId, leaving all switches linked after that date with NULL.
--
-- Strategy: derive the correct geometry_switch_id using the same topology traversal that
-- fetchSwitchLinkStatuses uses, filtered to main_official context to avoid false matches
-- from stale or superseded geometry plans. Only unambiguous cases (exactly one geometry switch
-- per layout switch) are updated. The DB trigger creates a new switch_version row on each UPDATE.
-- Historical version rows from the regression period remain NULL, which is acceptable.
with linked_edge as (
    select distinct
        gs.id                                         as geometry_switch_id,
        es.edge_id,
        coalesce(start_n.switch_id, end_n.switch_id)  as layout_switch_id
    from geometry.switch gs
        inner join geometry.element ge on ge.switch_id = gs.id
        inner join layout.edge_segment es
                   on es.geometry_alignment_id = ge.alignment_id
                     and es.geometry_element_index = ge.element_index
        inner join layout.edge le on le.id = es.edge_id
        left join layout.node_port start_n
                  on es.segment_index = 0
                    and le.start_node_id = start_n.node_id
                    and le.start_node_port = start_n.port
        left join layout.node_port end_n
                  on es.segment_index = (le.segment_count - 1)
                    and le.start_node_id = end_n.node_id
                    and le.start_node_port = end_n.port
    where start_n.switch_id is not null or end_n.switch_id is not null
),
linked_switch_pairs as (
    select distinct le.geometry_switch_id, le.layout_switch_id
    from linked_edge le
        inner join layout.location_track_version_edge ltve on ltve.edge_id = le.edge_id
        inner join layout.switch_in_layout_context('OFFICIAL'::layout.publication_state, null) ls
                   on ls.id = le.layout_switch_id
        inner join layout.location_track_in_layout_context('OFFICIAL'::layout.publication_state, null) lt
                   on ltve.location_track_id = lt.id
                     and ltve.location_track_layout_context_id = lt.layout_context_id
                     and ltve.location_track_version = lt.version
                     and lt.state != 'DELETED'
),
unambiguous_pairs as (
    select max(geometry_switch_id) as geometry_switch_id, layout_switch_id
    from linked_switch_pairs
    group by layout_switch_id
    having count(*) = 1
)
update layout.switch ls
set geometry_switch_id = pairs.geometry_switch_id
from unambiguous_pairs pairs
where ls.id = pairs.layout_switch_id
  and ls.geometry_switch_id is null;
