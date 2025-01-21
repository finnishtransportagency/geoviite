-- location track versions

-- temporary drops to allow version history to be rewritten
alter table publication.location_track
  drop constraint publication_location_track_location_track_version_fk;
alter table publication.switch_location_tracks
  drop constraint publication_switch_location_tracks_location_track_version_fk;

alter table layout.location_track_version
  drop constraint location_track_version_pkey;

alter table publication.location_track
  add column layout_context_id text;
alter table publication.switch_location_tracks
  add column location_track_layout_context_id text;
update publication.location_track
set layout_context_id = 'main_official';
update publication.switch_location_tracks
set location_track_layout_context_id = 'main_official';
alter table publication.location_track alter column layout_context_id set not null;
alter table publication.switch_location_tracks alter column location_track_layout_context_id set not null;

create temporary table location_track_version_change on commit drop as
  (
    select
      layout_context_id,
      id,
      version as old_version,
          row_number() over (partition by layout_context_id, official_id order by change_time, version) as new_version
      from layout.location_track_version
  );

update publication.location_track
set location_track_version = location_track_version_change.new_version
  from location_track_version_change
  where location_track.layout_context_id = location_track_version_change.layout_context_id
    and location_track.location_track_id = location_track_version_change.id
    and location_track.location_track_version = location_track_version_change.old_version;

update publication.switch_location_tracks
  set location_track_version = location_track_version_change.new_version
  from location_track_version_change
  where switch_location_tracks.location_track_layout_context_id = location_track_version_change.layout_context_id
    and switch_location_tracks.location_track_id = location_track_version_change.id
    and switch_location_tracks.location_track_version = location_track_version_change.old_version;

update layout.location_track_version
set version = location_track_version_change.new_version, id = official_id
  from location_track_version_change
  where location_track_version.layout_context_id = location_track_version_change.layout_context_id
    and location_track_version.id = location_track_version_change.id
    and location_track_version.version = location_track_version_change.old_version;

alter table layout.location_track_version
  add constraint location_track_version_pkey primary key (id, layout_context_id, version);

alter table publication.location_track
  add constraint publication_location_track_location_track_version_fk
    foreign key (location_track_id, layout_context_id, location_track_version)
      references layout.location_track_version (id, layout_context_id, version);

alter table layout.location_track_version
  add column origin_design_id int;
update layout.location_track_version
set origin_design_id = design_row.design_id
  from layout.location_track_version design_row
  where design_row.id = location_track_version.design_row_id
    and not exists (
    select *
      from layout.location_track_version future_design_row
      where future_design_row.id = design_row.id
        and future_design_row.layout_context_id = design_row.layout_context_id
        and future_design_row.version > design_row.version
  );

alter table layout.location_track_version
  drop column official_id;
alter table layout.location_track_version
  drop column design_row_id;
alter table layout.location_track_version
  drop column official_row_id;
