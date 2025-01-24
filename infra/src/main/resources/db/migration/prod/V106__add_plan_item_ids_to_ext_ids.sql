alter table layout.track_number_external_id
  add column plan_item_id int;

alter table layout.location_track_external_id
  add column plan_item_id int;

alter table layout.switch_external_id
  add column plan_item_id int;

alter table layout.track_number_external_id_version
  add column plan_item_id int;

alter table layout.location_track_external_id_version
  add column plan_item_id int;

alter table layout.switch_external_id_version
  add column plan_item_id int;
