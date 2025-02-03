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

drop index if exists layout.node_switch_in_ix;
create index node_switch_in_ix on layout.node (switch_in_id, switch_in_joint_number);

drop index if exists layout.node_switch_out_ix;
create index node_switch_out_ix on layout.node (switch_out_id, switch_out_joint_number);
