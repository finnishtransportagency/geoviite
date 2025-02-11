drop index if exists layout.location_track_version_edge_edge_ix;
create index location_track_version_edge_edge_ix on layout.location_track_version_edge (edge_id);

drop index if exists layout.edge_segment_edge_ix;
create index edge_segment_edge_ix on layout.edge_segment (edge_id);

drop index if exists layout.edge_segment_segment_geometry_ix;
create index edge_segment_segment_geometry_ix on layout.edge_segment (geometry_id);

drop index if exists layout.edge_start_node_ix;
create index edge_start_node_ix on layout.edge (start_node_id);

drop index if exists layout.edge_end_node_ix;
create index edge_end_node_ix on layout.edge (end_node_id);

drop index if exists layout.node_switch_1_ix;
create index node_switch_1_ix on layout.node (switch_1_id, switch_1_joint_number);

drop index if exists layout.node_switch_2_ix;
create index node_switch_2_ix on layout.node (switch_2_id, switch_2_joint_number);
