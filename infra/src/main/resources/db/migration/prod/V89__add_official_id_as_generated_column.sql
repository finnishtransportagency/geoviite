alter table layout.track_number disable trigger version_update_trigger;
alter table layout.track_number disable trigger version_row_trigger;

alter table layout.reference_line disable trigger version_update_trigger;
alter table layout.reference_line disable trigger version_row_trigger;

alter table layout.location_track disable trigger version_update_trigger;
alter table layout.location_track disable trigger version_row_trigger;

alter table layout.switch disable trigger version_update_trigger;
alter table layout.switch disable trigger version_row_trigger;

alter table layout.km_post disable trigger version_update_trigger;
alter table layout.km_post disable trigger version_row_trigger;

alter table layout.track_number
  add column official_id integer not null references layout.track_number(id)
    generated always as (coalesce(official_row_id, design_row_id, id)) stored;

alter table layout.track_number_version add column official_id integer null;
update layout.track_number_version set official_id = (coalesce(official_row_id, design_row_id, id)) where true;
alter table layout.track_number_version alter column official_id set not null;

alter table layout.reference_line
  add column official_id integer not null references layout.reference_line(id)
    generated always as (coalesce(official_row_id, design_row_id, id)) stored;

alter table layout.reference_line_version add column official_id integer null;
update layout.reference_line_version set official_id = (coalesce(official_row_id, design_row_id, id)) where true;
alter table layout.reference_line_version alter column official_id set not null;

alter table layout.location_track
  add column official_id integer not null references layout.location_track(id)
    generated always as (coalesce(official_row_id, design_row_id, id)) stored;

alter table layout.location_track_version add column official_id integer null;
update layout.location_track_version set official_id = (coalesce(official_row_id, design_row_id, id)) where true;
alter table layout.location_track_version alter column official_id set not null;

alter table layout.switch
  add column official_id integer not null references layout.switch(id)
    generated always as (coalesce(official_row_id, design_row_id, id)) stored;

alter table layout.switch_version add column official_id integer null;
update layout.switch_version set official_id = (coalesce(official_row_id, design_row_id, id)) where true;
alter table layout.switch_version alter column official_id set not null;

alter table layout.km_post
  add column official_id integer not null references layout.km_post(id)
    generated always as (coalesce(official_row_id, design_row_id, id)) stored;

alter table layout.km_post_version add column official_id integer null;
update layout.km_post_version set official_id = (coalesce(official_row_id, design_row_id, id)) where true;
alter table layout.km_post_version alter column official_id set not null;

alter table layout.track_number enable trigger version_update_trigger;
alter table layout.track_number enable trigger version_row_trigger;

alter table layout.reference_line enable trigger version_update_trigger;
alter table layout.reference_line enable trigger version_row_trigger;

alter table layout.location_track enable trigger version_update_trigger;
alter table layout.location_track enable trigger version_row_trigger;

alter table layout.switch enable trigger version_update_trigger;
alter table layout.switch enable trigger version_row_trigger;

alter table layout.km_post enable trigger version_update_trigger;
alter table layout.km_post enable trigger version_row_trigger;
