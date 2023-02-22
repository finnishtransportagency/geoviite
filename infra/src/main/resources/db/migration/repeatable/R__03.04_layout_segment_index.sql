drop index if exists layout.layout_segment_switch;
drop index if exists layout.layout_segment_version_switch;
create index layout_segment_version_switch on layout.segment_version(switch_id);

drop index if exists layout.layout_segment_geometry_element;
drop index if exists layout.layout_segment_version_geometry_element;
create index layout_segment_version_geometry_element on layout.segment_version(geometry_alignment_id, geometry_element_index);

-- Deprecated index, drop if it still exists
drop index if exists layout.layout_segment_bounding_box_index;

drop index if exists layout.layout_segment_version_segment_geometry;
create index layout_segment_version_segment_geometry on layout.segment_version(geometry_id);

drop index if exists layout.layout_segment_geometry_bounding_box_index;
create index layout_segment_geometry_bounding_box_index on layout.segment_geometry using gist (bounding_box);
