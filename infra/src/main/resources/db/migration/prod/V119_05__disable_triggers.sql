-- Trigger disable/enable in a separate file to get it in it's own transaction
-- This is necessary to avoid issues from deferrer constraints
alter table layout.location_track
  disable trigger version_update_trigger,
  disable trigger version_row_trigger;
alter table layout.alignment
  disable trigger version_row_trigger,
  disable trigger version_update_trigger;
