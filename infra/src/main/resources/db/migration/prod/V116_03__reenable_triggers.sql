alter table layout.location_track
  enable trigger version_update_trigger;
alter table layout.location_track
  enable trigger version_row_trigger;

alter table layout.track_number
  enable trigger version_update_trigger;
alter table layout.track_number
  enable trigger version_row_trigger;

alter table layout.switch
  enable trigger version_update_trigger;
alter table layout.switch
  enable trigger version_row_trigger;