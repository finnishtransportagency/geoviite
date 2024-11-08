-- Increment to recreate the index: 1
drop index if exists geometry.geometry_plan_polygon_simple_index;
create index geometry_plan_polygon_simple_index on geometry.plan using gist (bounding_polygon_simple);
