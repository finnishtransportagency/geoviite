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

-- Update segment table structure
drop table layout.segment;
delete from layout.segment_version where deleted = true;
alter table layout.segment_version
  drop constraint segment_version_pkey, -- Remove old primary key
  drop column change_time, -- Metadata is on alignment, which changes as a whole -> this is duplicate info
  drop column change_user, -- Metadata is on alignment, which changes as a whole -> this is duplicate info
  drop column version, -- Alignment versioning is sufficient as segments don't change independently
  drop column deleted, -- Deleted rows: the new alignment version just doesn't have a segment for the index
  drop column length, -- This is currently the m-value of the last point -> no separate column needed
  drop column start, -- This is the sum of lengths by-index -> no separate column needed
  add column geometry_id int null, -- Reference to the new geometry table
  add primary key (alignment_id, alignment_version, segment_index) -- Add new primary key
;

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

-- Remove the transferred from the segment table
alter table layout.segment_version
  drop column resolution,
  drop column geometry,
  drop column height_values,
  drop column cant_values,
  drop column bounding_box,
  alter column geometry_id set not null;

-- Add foreign key references for version table, as there is no separate main table any more
alter table layout.segment_version
  add constraint segment_segment_geometry_fkey
    foreign key (geometry_id) references layout.segment_geometry (id),
  add constraint segment_version_alignment_version_fkey
    foreign key(alignment_id, alignment_version) references layout.alignment_version(id, version),
--   add constraint segment_version_switch_version_fkey
--     foreign key (switch_id, switch_version) references layout.switch_version (id, version),
  add constraint segment_version_geometry_alignment_fkey
    foreign key (geometry_alignment_id) references geometry.alignment (id),
  add constraint segment_version_geometry_element_fkey
    foreign key (geometry_alignment_id, geometry_element_index) references geometry.element (alignment_id, element_index);
