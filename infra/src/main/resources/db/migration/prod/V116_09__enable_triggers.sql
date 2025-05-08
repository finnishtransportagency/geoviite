-- Trigger disable/enable in a separate file to get it in it's own transaction
-- This is necessary to avoid issues from deferrer constraints
alter table layout.alignment
  enable trigger version_row_trigger,
  enable trigger version_update_trigger;
alter table layout.location_track
  enable trigger version_row_trigger,
  enable trigger version_update_trigger;
