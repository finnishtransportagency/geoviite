-- New quality enum: separates reliability from measurement technique
create type geometry.plan_quality as enum ('PLAN', 'UNRELIABLE_PLAN');

-- Add quality column (nullable — new uploads will not have this until operator sets it)
alter table geometry.plan add column quality geometry.plan_quality;
alter table geometry.plan_version add column quality geometry.plan_quality;

-- Drop old plan_applicability (now computed from quality + plan_decision in application code)
alter table geometry.plan drop column plan_applicability;
alter table geometry.plan_version drop column plan_applicability;
drop type geometry.plan_applicability;

-- DATA MIGRATION
-- ============================================================
-- Classify existing plans into the new quality/source model.
-- Plans from GEOMETRIAPALVELU and PAIKANNUSPALVELU are migrated;
-- GEOVIITE plans (none yet in prod) are left untouched.
--
-- The same logic is applied to both plan_version and plan so that
-- any historical row remains readable with current code.
--
-- Initial-import rows: first plan_version row has change_user = 'IM_IMPORT'
-- All other rows are operator-uploaded.
-- ============================================================

create type geometry.updated_plan_fields as
(
    quality            geometry.plan_quality,
    measurement_method common.measurement_method,
    source             geometry.plan_source
);

-- Compute migrated (quality, measurement_method, source) for a plan row.
-- Legacy measurement method values are normalised before the case logic:
--   VERIFIED_DESIGNED_GEOMETRY   -> OFFICIALLY_MEASURED_GEODETICALLY
--   UNVERIFIED_DESIGNED_GEOMETRY -> DIGITIZED_AERIAL_IMAGE
-- Returns null for plans not in scope (e.g. source = GEOVIITE).
create function geometry.migrated_plan_fields(
    id_in                 int,
    measurement_method_in common.measurement_method,
    source_in             geometry.plan_source
) returns geometry.updated_plan_fields
as
$$
select
    case
        when source_in = 'GEOMETRIAPALVELU' and is_import
            then row('PLAN', 'OFFICIALLY_MEASURED_GEODETICALLY', source_in)::geometry.updated_plan_fields
        when source_in = 'GEOMETRIAPALVELU' and not is_import and norm_mm = 'OFFICIALLY_MEASURED_GEODETICALLY'
            then row('PLAN', 'OFFICIALLY_MEASURED_GEODETICALLY', 'GEOVIITE')::geometry.updated_plan_fields
        when source_in = 'GEOMETRIAPALVELU' and not is_import and norm_mm in ('TRACK_INSPECTION', 'DIGITIZED_AERIAL_IMAGE')
            then row('UNRELIABLE_PLAN', norm_mm, 'GEOVIITE')::geometry.updated_plan_fields
        when source_in = 'GEOMETRIAPALVELU' and not is_import
            then row('UNRELIABLE_PLAN', 'DIGITIZED_AERIAL_IMAGE', 'GEOVIITE')::geometry.updated_plan_fields
        when source_in = 'PAIKANNUSPALVELU' and is_import and norm_mm = 'OFFICIALLY_MEASURED_GEODETICALLY'
            then row('UNRELIABLE_PLAN', 'OFFICIALLY_MEASURED_GEODETICALLY', source_in)::geometry.updated_plan_fields
        when source_in = 'PAIKANNUSPALVELU' and is_import and norm_mm in ('TRACK_INSPECTION', 'DIGITIZED_AERIAL_IMAGE')
            then row('UNRELIABLE_PLAN', norm_mm, source_in)::geometry.updated_plan_fields
        when source_in = 'PAIKANNUSPALVELU' and is_import
            then row('UNRELIABLE_PLAN', 'DIGITIZED_AERIAL_IMAGE', source_in)::geometry.updated_plan_fields
        when source_in = 'PAIKANNUSPALVELU' and not is_import and norm_mm = 'OFFICIALLY_MEASURED_GEODETICALLY'
            then row('UNRELIABLE_PLAN', 'OFFICIALLY_MEASURED_GEODETICALLY', 'GEOVIITE')::geometry.updated_plan_fields
        when source_in = 'PAIKANNUSPALVELU' and not is_import and norm_mm in ('TRACK_INSPECTION', 'DIGITIZED_AERIAL_IMAGE')
            then row('UNRELIABLE_PLAN', norm_mm, 'GEOVIITE')::geometry.updated_plan_fields
        when source_in = 'PAIKANNUSPALVELU' and not is_import
            then row('UNRELIABLE_PLAN', 'DIGITIZED_AERIAL_IMAGE', 'GEOVIITE')::geometry.updated_plan_fields
    end
from (
    select
        (select change_user
           from geometry.plan_version
          where id = id_in
          order by version asc
          limit 1) = 'IM_IMPORT' as is_import,
        case measurement_method_in
            when 'VERIFIED_DESIGNED_GEOMETRY'   then 'OFFICIALLY_MEASURED_GEODETICALLY'::common.measurement_method
            when 'UNVERIFIED_DESIGNED_GEOMETRY' then 'DIGITIZED_AERIAL_IMAGE'::common.measurement_method
            else measurement_method_in
        end as norm_mm
) derived
$$ language sql;

update geometry.plan_version pv
set (quality, measurement_method, source) = (
    select mf.quality, mf.measurement_method, mf.source
      from geometry.migrated_plan_fields(pv.id, pv.measurement_method, pv.source) mf
)
where source in ('GEOMETRIAPALVELU', 'PAIKANNUSPALVELU');

alter table geometry.plan
    disable trigger version_update_trigger,
    disable trigger version_row_trigger;

update geometry.plan p
set (quality, measurement_method, source) = (
    select mf.quality, mf.measurement_method, mf.source
      from geometry.migrated_plan_fields(p.id, p.measurement_method, p.source) mf
)
where source in ('GEOMETRIAPALVELU', 'PAIKANNUSPALVELU');

alter table geometry.plan
    enable trigger version_update_trigger,
    enable trigger version_row_trigger;

drop function geometry.migrated_plan_fields;
drop type geometry.updated_plan_fields;
