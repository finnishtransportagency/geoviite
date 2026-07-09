-- New quality enum: separates reliability from measurement technique
create type geometry.plan_quality as enum ('PLAN', 'UNRELIABLE_PLAN');

-- Add quality column (nullable — new uploads will not have this until operator sets it)
alter table geometry.plan add column quality geometry.plan_quality;
alter table geometry.plan_version add column quality geometry.plan_quality;

-- ============================================================
-- Data migration
-- Initial-import rows: first plan_version row has change_user = 'IM_IMPORT'
-- All other rows are operator-uploaded.
-- ============================================================

-- GEOMETRIAPALVELU + initial import → quality=PLAN, mm=OFFICIALLY_MEASURED_GEODETICALLY
update geometry.plan
set quality = 'PLAN',
    measurement_method = 'OFFICIALLY_MEASURED_GEODETICALLY'
where source = 'GEOMETRIAPALVELU'
  and (select change_user from geometry.plan_version where id = plan.id order by change_time asc limit 1) = 'IM_IMPORT';

-- GEOMETRIAPALVELU + operator + VERIFIED or OFFICIALLY → quality=PLAN, mm=OFFICIALLY, source=GEOVIITE
update geometry.plan
set quality = 'PLAN',
    measurement_method = 'OFFICIALLY_MEASURED_GEODETICALLY',
    source = 'GEOVIITE'
where source = 'GEOMETRIAPALVELU'
  and (select change_user from geometry.plan_version where id = plan.id order by change_time asc limit 1) != 'IM_IMPORT'
  and measurement_method in ('VERIFIED_DESIGNED_GEOMETRY', 'OFFICIALLY_MEASURED_GEODETICALLY');

-- GEOMETRIAPALVELU + operator + TRACK or DIGITIZED → quality=UNRELIABLE_PLAN, mm preserved, source=GEOVIITE
update geometry.plan
set quality = 'UNRELIABLE_PLAN',
    source = 'GEOVIITE'
where source = 'GEOMETRIAPALVELU'
  and (select change_user from geometry.plan_version where id = plan.id order by change_time asc limit 1) != 'IM_IMPORT'
  and measurement_method in ('TRACK_INSPECTION', 'DIGITIZED_AERIAL_IMAGE');

-- GEOMETRIAPALVELU + operator + UNVERIFIED or null → quality=UNRELIABLE_PLAN, mm=DIGITIZED, source=GEOVIITE
update geometry.plan
set quality = 'UNRELIABLE_PLAN',
    measurement_method = 'DIGITIZED_AERIAL_IMAGE',
    source = 'GEOVIITE'
where source = 'GEOMETRIAPALVELU'
  and (select change_user from geometry.plan_version where id = plan.id order by change_time asc limit 1) != 'IM_IMPORT'
  and (measurement_method = 'UNVERIFIED_DESIGNED_GEOMETRY' or measurement_method is null);

-- PAIKANNUSPALVELU + initial import + VERIFIED or OFFICIALLY → quality=UNRELIABLE_PLAN, mm=OFFICIALLY
update geometry.plan
set quality = 'UNRELIABLE_PLAN',
    measurement_method = 'OFFICIALLY_MEASURED_GEODETICALLY'
where source = 'PAIKANNUSPALVELU'
  and (select change_user from geometry.plan_version where id = plan.id order by change_time asc limit 1) = 'IM_IMPORT'
  and measurement_method in ('VERIFIED_DESIGNED_GEOMETRY', 'OFFICIALLY_MEASURED_GEODETICALLY');

-- PAIKANNUSPALVELU + initial import + TRACK or DIGITIZED → quality=UNRELIABLE_PLAN, mm preserved
update geometry.plan
set quality = 'UNRELIABLE_PLAN'
where source = 'PAIKANNUSPALVELU'
  and (select change_user from geometry.plan_version where id = plan.id order by change_time asc limit 1) = 'IM_IMPORT'
  and measurement_method in ('TRACK_INSPECTION', 'DIGITIZED_AERIAL_IMAGE');

-- PAIKANNUSPALVELU + initial import + UNVERIFIED or null → quality=UNRELIABLE_PLAN, mm=DIGITIZED
update geometry.plan
set quality = 'UNRELIABLE_PLAN',
    measurement_method = 'DIGITIZED_AERIAL_IMAGE'
where source = 'PAIKANNUSPALVELU'
  and (select change_user from geometry.plan_version where id = plan.id order by change_time asc limit 1) = 'IM_IMPORT'
  and (measurement_method = 'UNVERIFIED_DESIGNED_GEOMETRY' or measurement_method is null);

-- PAIKANNUSPALVELU + operator + VERIFIED or OFFICIALLY → quality=UNRELIABLE_PLAN, mm=OFFICIALLY, source=GEOVIITE
update geometry.plan
set quality = 'UNRELIABLE_PLAN',
    measurement_method = 'OFFICIALLY_MEASURED_GEODETICALLY',
    source = 'GEOVIITE'
where source = 'PAIKANNUSPALVELU'
  and (select change_user from geometry.plan_version where id = plan.id order by change_time asc limit 1) != 'IM_IMPORT'
  and measurement_method in ('VERIFIED_DESIGNED_GEOMETRY', 'OFFICIALLY_MEASURED_GEODETICALLY');

-- PAIKANNUSPALVELU + operator + TRACK or DIGITIZED → quality=UNRELIABLE_PLAN, mm preserved, source=GEOVIITE
update geometry.plan
set quality = 'UNRELIABLE_PLAN',
    source = 'GEOVIITE'
where source = 'PAIKANNUSPALVELU'
  and (select change_user from geometry.plan_version where id = plan.id order by change_time asc limit 1) != 'IM_IMPORT'
  and measurement_method in ('TRACK_INSPECTION', 'DIGITIZED_AERIAL_IMAGE');

-- PAIKANNUSPALVELU + operator + UNVERIFIED or null → quality=UNRELIABLE_PLAN, mm=DIGITIZED, source=GEOVIITE
update geometry.plan
set quality = 'UNRELIABLE_PLAN',
    measurement_method = 'DIGITIZED_AERIAL_IMAGE',
    source = 'GEOVIITE'
where source = 'PAIKANNUSPALVELU'
  and (select change_user from geometry.plan_version where id = plan.id order by change_time asc limit 1) != 'IM_IMPORT'
  and (measurement_method = 'UNVERIFIED_DESIGNED_GEOMETRY' or measurement_method is null);

-- Drop old plan_applicability (now computed from quality + plan_decision in application code)
alter table geometry.plan drop column plan_applicability;
alter table geometry.plan_version drop column plan_applicability;
drop type geometry.plan_applicability;
