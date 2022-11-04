drop index if exists layout.layout_segment_switch;
create index layout_segment_switch on layout.segment(switch_id);

drop index if exists layout.layout_segment_geometry_element;
create index layout_segment_geometry_element on layout.segment(geometry_alignment_id, geometry_element_index);

drop index if exists layout.layout_segment_bounding_box_index;
create index layout_segment_bounding_box_index on layout.segment using gist (bounding_box);
