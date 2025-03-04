create index pgrouting_node_location on pgrouting.node using gist (location);
create index pgrouting_edge_start_node_id on pgrouting.edge (start_node_id);
create index pgrouting_edge_end_node_id on pgrouting.edge (end_node_id);
