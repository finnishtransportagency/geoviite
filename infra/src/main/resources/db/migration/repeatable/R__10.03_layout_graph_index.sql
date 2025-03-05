drop index if exists layout.location_track_version_edge_edge_ix;
create index location_track_version_edge_edge_ix on layout.location_track_version_edge (edge_id, location_track_id, location_track_layout_context_id, location_track_version);

drop index if exists layout.edge_segment_edge_ix;
create index edge_segment_edge_ix on layout.edge_segment (edge_id);

drop index if exists layout.edge_segment_segment_geometry_ix;
create index edge_segment_segment_geometry_ix on layout.edge_segment (geometry_id, edge_id);

drop index if exists layout.edge_start_node_ix;
create index edge_start_node_ix on layout.edge (start_node_id, start_node_port);

drop index if exists layout.edge_end_node_ix;
create index edge_end_node_ix on layout.edge (end_node_id, end_node_port);

drop index if exists layout.node_port_switch_ix;
create index node_port_switch_ix on layout.node_port (switch_id, switch_joint_number);

drop index if exists layout.node_port_node_type_ix;
create index node_port_node_type_ix on layout.node_port (node_id, node_type);

drop index if exists layout.edge_segment_geometry_element_ix;
create index edge_segment_geometry_element_ix on layout.edge_segment (geometry_alignment_id, geometry_element_index);

drop index if exists layout.location_track_version_change_ix;
create index location_track_version_change_ix on layout.location_track_version (change_time, change_user);
