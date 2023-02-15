-- Immutable helper function for generating hashes on geometry data
create or replace function layout.calculate_geometry_hash(
  resolution int,
  geometry postgis.geometry,
  height_values numeric[],
  cant_values numeric[]
) returns uuid language sql as $$
  select md5(row(resolution, geometry, height_values, cant_values)::text)::uuid
$$ immutable;

-- Create new un-versioned table for separately stored geometries
create table layout.segment_geometry
(
  id            int primary key generated always as identity,
  resolution    int                                 not null,
  geometry      postgis.geometry(linestringm, 3067) not null,
  height_values decimal(10, 6)[]                    null,
  cant_values   decimal(10, 6)[]                    null,
  bounding_box  postgis.geometry                    not null
    generated always as (postgis.st_envelope(geometry)) stored,
  hash          uuid                                not null unique
    generated always as (
      layout.calculate_geometry_hash(resolution, geometry, height_values, cant_values)
    ) stored
);

alter table layout.segment_version add column geometry_id int null;

-- Copy all known geometries to the new table, preserving the the ids
insert into layout.segment_geometry(resolution, geometry, height_values, cant_values)
select resolution, geometry, height_values, cant_values
  from layout.segment_version
on conflict(hash) do nothing;

-- Update new geometry ids to version table
update layout.segment_version sv
set geometry_id = segment_geometry.id
from layout.segment_geometry
where segment_geometry.hash = layout.calculate_geometry_hash(
  sv.resolution,
  sv.geometry,
  sv.height_values,
  sv.cant_values
  );

-- Update segment_version to use new table's data instead of own
alter table layout.segment_version
  drop column resolution,
  drop column geometry,
  drop column height_values,
  drop column cant_values,
  drop column bounding_box,
  alter column geometry_id set not null;

-- Disable versioning triggers on the main table so the following edits don't create version rows
alter table layout.segment disable trigger version_row_trigger;
alter table layout.segment disable trigger version_update_trigger;

-- Remove indexes relying on old columns (new indices will be added in a repeatable migration)
drop index if exists layout.layout_segment_bounding_box_index;

-- Swap geometry data for gemetry table id in the main table (null for now)
alter table layout.segment
  drop column resolution,
  drop column geometry,
  drop column height_values,
  drop column cant_values,
  add column geometry_id int null;

-- Collect all geometry table references from the current version rows
update layout.segment
set geometry_id = sv.geometry_id
  from layout.segment_version sv
  where sv.alignment_id = segment.alignment_id
    and sv.segment_index = segment.segment_index
    and sv.version = segment.version;

-- Set geometry id as mandatory + add foreign key reference
alter table layout.segment
  alter column geometry_id set not null,
  add constraint segment_segment_geometry_id_fkey foreign key (geometry_id) references layout.segment_geometry (id);

-- Re-enable versioning triggers
alter table layout.segment enable trigger version_update_trigger;
alter table layout.segment enable trigger version_row_trigger;

-- Full vacuum analyse for perf & storage space, as the tables have changed drastically
vacuum full analyse layout.segment_version;
vacuum full analyse layout.segment;
