alter table layout.location_track
  disable trigger version_update_trigger;
alter table layout.location_track
  disable trigger version_row_trigger;

drop view if exists layout.location_track_change_view;

alter table layout.location_track
  alter column name type varchar(87);
alter table layout.location_track_version
  alter column name type varchar(87);

alter table layout.location_track
  enable trigger version_update_trigger;
alter table layout.location_track
  enable trigger version_row_trigger;
