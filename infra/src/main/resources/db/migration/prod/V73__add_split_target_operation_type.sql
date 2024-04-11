create type publication.split_target_operaton as enum ('CREATE', 'OVERWRITE', 'TRANSFER');

alter table publication.split_target_location_track disable trigger version_update_trigger;
alter table publication.split_target_location_track disable trigger version_row_trigger;

-- This is intentionally added as non-null as there should be no production data as of yet and the migration would be complex
-- If there is data in the table, it's better to fail than invent a bogus default value
alter table publication.split_target_location_track_version
  add column operation publication.split_target_operaton not null;
alter table publication.split_target_location_track
  add column operation publication.split_target_operaton not null;

alter table publication.split_target_location_track enable trigger version_row_trigger;
alter table publication.split_target_location_track enable trigger version_update_trigger;
