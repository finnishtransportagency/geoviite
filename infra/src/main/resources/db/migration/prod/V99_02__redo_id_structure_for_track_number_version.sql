-- track number versions

-- temporary drop to allow version history to be rewritten
alter table publication.track_number
  drop constraint publication_track_number_track_number_version_fk;

alter table layout.track_number_version
  drop constraint track_number_version_pkey;

alter table publication.track_number
  add column layout_context_id text;
update publication.track_number
set layout_context_id = 'main_official';
alter table publication.track_number alter column layout_context_id set not null;

-- pattern for all foo_version_change tables: The partitioning is over the version table's
-- new primary key.
create temporary table track_number_version_change on commit drop as
  (
    select
      layout_context_id,
      id,
      version as old_version,
          row_number() over (partition by layout_context_id, official_id order by change_time, version) as new_version
      from layout.track_number_version
  );

update publication.track_number
set track_number_version = track_number_version_change.new_version
  from track_number_version_change
  where track_number.layout_context_id = track_number_version_change.layout_context_id
    and track_number.track_number_id = track_number_version_change.id
    and track_number.track_number_version = track_number_version_change.old_version;

update layout.track_number_version
set version = track_number_version_change.new_version, id = official_id
  from track_number_version_change
  where track_number_version.layout_context_id = track_number_version_change.layout_context_id
    and track_number_version.id = track_number_version_change.id
    and track_number_version.version = track_number_version_change.old_version;

alter table layout.track_number_version
  add constraint track_number_version_pkey primary key (id, layout_context_id, version);

alter table publication.track_number
  add constraint publication_track_number_track_number_version_fk
    foreign key (track_number_id, layout_context_id, track_number_version)
      references layout.track_number_version (id, layout_context_id, version);

alter table layout.track_number_version
  add column origin_design_id int;
update layout.track_number_version
set origin_design_id = design_row.design_id
  from layout.track_number_version design_row
  where design_row.id = track_number_version.design_row_id
    and not exists (
    select *
      from layout.track_number_version future_design_row
      where future_design_row.id = design_row.id
      and future_design_row.layout_context_id = design_row.layout_context_id
      and future_design_row.version > design_row.version
  );

alter table layout.track_number_version
  drop column official_id;
alter table layout.track_number_version
  drop column design_row_id;
alter table layout.track_number_version
  drop column official_row_id;
