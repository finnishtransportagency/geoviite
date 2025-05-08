alter table layout.location_track
  disable trigger version_update_trigger;
alter table layout.location_track
  disable trigger version_row_trigger;

alter table layout.track_number
  disable trigger version_update_trigger;
alter table layout.track_number
  disable trigger version_row_trigger;

alter table layout.switch
  disable trigger version_update_trigger;
alter table layout.switch
  disable trigger version_row_trigger;
