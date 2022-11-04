drop index if exists geometry.geometry_km_post_plan;
create index geometry_km_post_plan on geometry.km_post(plan_id);
