alter table layout.track_number
  disable trigger version_update_trigger;
alter table layout.track_number
  disable trigger version_row_trigger;

alter table layout.reference_line
  disable trigger version_update_trigger;
alter table layout.reference_line
  disable trigger version_row_trigger;

alter table layout.location_track
  disable trigger version_update_trigger;
alter table layout.location_track
  disable trigger version_row_trigger;

alter table layout.switch
  disable trigger version_update_trigger;
alter table layout.switch
  disable trigger version_row_trigger;

alter table layout.km_post
  disable trigger version_update_trigger;
alter table layout.km_post
  disable trigger version_row_trigger;

alter table layout.track_number
  add column cancelled boolean not null default false;

alter table layout.track_number_version
  add column cancelled boolean null;
update layout.track_number_version set cancelled = false where true;
alter table layout.track_number_version
  alter column cancelled set not null;

alter table layout.reference_line
  add column cancelled boolean not null default false;

alter table layout.reference_line_version
  add column cancelled boolean null;
update layout.reference_line_version set cancelled = false where true;
alter table layout.reference_line_version
  alter column cancelled set not null;

alter table layout.location_track
  add column cancelled boolean not null default false;

alter table layout.location_track_version
  add column cancelled boolean null;
update layout.location_track_version set cancelled = false where true;
alter table layout.location_track_version
  alter column cancelled set not null;

alter table layout.switch
  add column cancelled boolean not null default false;

alter table layout.switch_version
  add column cancelled boolean null;
update layout.switch_version set cancelled = false where true;
alter table layout.switch_version
  alter column cancelled set not null;

alter table layout.km_post
  add column cancelled boolean not null default false;

alter table layout.km_post_version
  add column cancelled boolean null;
update layout.km_post_version set cancelled = false where true;
alter table layout.km_post_version
  alter column cancelled set not null;

alter table layout.track_number
  enable trigger version_update_trigger;
alter table layout.track_number
  enable trigger version_row_trigger;

alter table layout.reference_line
  enable trigger version_update_trigger;
alter table layout.reference_line
  enable trigger version_row_trigger;

alter table layout.location_track
  enable trigger version_update_trigger;
alter table layout.location_track
  enable trigger version_row_trigger;

alter table layout.switch
  enable trigger version_update_trigger;
alter table layout.switch
  enable trigger version_row_trigger;

alter table layout.km_post
  enable trigger version_update_trigger;
alter table layout.km_post
  enable trigger version_row_trigger;


alter table layout.track_number
  add constraint cancel_only_in_design check (not (design_id is null and cancelled));
alter table layout.reference_line
  add constraint cancel_only_in_design check (not (design_id is null and cancelled));
alter table layout.location_track
  add constraint cancel_only_in_design check (not (design_id is null and cancelled));
alter table layout.switch
  add constraint cancel_only_in_design check (not (design_id is null and cancelled));
alter table layout.km_post
  add constraint cancel_only_in_design check (not (design_id is null and cancelled));
