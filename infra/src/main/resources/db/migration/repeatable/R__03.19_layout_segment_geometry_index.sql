drop index if exists layout.layout_segment_geometry_bounding_box_index;
create index layout_segment_geometry_bounding_box_index on layout.segment_geometry using gist (bounding_box);
