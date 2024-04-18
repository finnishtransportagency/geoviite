alter table layout.location_track
  add constraint location_track_id_version_unique unique (id, version);

alter table publication.split disable trigger version_update_trigger;
alter table publication.split disable trigger version_row_trigger;

-- The migration is breaking if there is data in the table, as there should not be at this point
alter table publication.split
  drop constraint split_source_location_track_fkey,
  add column source_location_track_row_id int not null,
  add column source_location_track_row_version int not null,
  drop column source_location_track_id,
  -- This fixes the split to a particular row version of the source location track
  -- By referencing the main table with version, we ensure that the track cannot be changed without updating the split
  add constraint split_source_location_track_fkey
    foreign key (source_location_track_row_id, source_location_track_row_version)
      references layout.location_track(id, version)
      -- Constraint checked at end of transaction to make it possible to update split after changing track:
      deferrable initially deferred;

alter table publication.split_version
  add column source_location_track_row_id int not null,
  add column source_location_track_row_version int not null,
  drop column source_location_track_id;

alter table publication.split enable trigger version_row_trigger;
alter table publication.split enable trigger version_update_trigger;
