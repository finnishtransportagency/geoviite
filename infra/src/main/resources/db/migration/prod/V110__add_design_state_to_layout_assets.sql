create type layout.design_asset_state as enum ('OPEN', 'COMPLETED', 'CANCELLED');

alter table layout.track_number
  disable trigger version_row_trigger;
alter table layout.track_number
  disable trigger version_update_trigger;
alter table layout.reference_line
  disable trigger version_row_trigger;
alter table layout.reference_line
  disable trigger version_update_trigger;
alter table layout.location_track
  disable trigger version_row_trigger;
alter table layout.location_track
  disable trigger version_update_trigger;
alter table layout.switch
  disable trigger version_row_trigger;
alter table layout.switch
  disable trigger version_update_trigger;
alter table layout.km_post
  disable trigger version_row_trigger;
alter table layout.km_post
  disable trigger version_update_trigger;


alter table layout.track_number_version
  add column design_asset_state layout.design_asset_state;
update layout.track_number_version
set design_asset_state = case when cancelled then 'CANCELLED' when design_id is not null then 'OPEN' end::layout.design_asset_state;
alter table layout.track_number_version drop column cancelled;

alter table layout.track_number
  add column design_asset_state layout.design_asset_state;
update layout.track_number
set design_asset_state = case when cancelled then 'CANCELLED' when design_id is not null then 'OPEN' end::layout.design_asset_state;
alter table layout.track_number
  add constraint track_number_design_asset_state_in_design_check check ((design_asset_state is null) = (design_id is null));
alter table layout.track_number drop column cancelled;


alter table layout.reference_line_version
  add column design_asset_state layout.design_asset_state;
update layout.reference_line_version
set design_asset_state = case when cancelled then 'CANCELLED' when design_id is not null then 'OPEN' end::layout.design_asset_state;
alter table layout.reference_line_version drop column cancelled;

alter table layout.reference_line
  add column design_asset_state layout.design_asset_state;
update layout.reference_line
set design_asset_state = case when cancelled then 'CANCELLED' when design_id is not null then 'OPEN' end::layout.design_asset_state;
alter table layout.reference_line
  add constraint reference_line_design_asset_state_in_design_check check ((design_asset_state is null) = (design_id is null));
alter table layout.reference_line drop column cancelled;


alter table layout.location_track_version
  add column design_asset_state layout.design_asset_state;
update layout.location_track_version
set design_asset_state = case when cancelled then 'CANCELLED' when design_id is not null then 'OPEN' end::layout.design_asset_state;
alter table layout.location_track_version drop column cancelled;

alter table layout.location_track
  add column design_asset_state layout.design_asset_state;
update layout.location_track
set design_asset_state = case when cancelled then 'CANCELLED' when design_id is not null then 'OPEN' end::layout.design_asset_state;
alter table layout.location_track
  add constraint location_track_design_asset_state_in_design_check check ((design_asset_state is null) = (design_id is null));
alter table layout.location_track drop column cancelled;


alter table layout.switch_version
  add column design_asset_state layout.design_asset_state;
update layout.switch_version
set design_asset_state = case when cancelled then 'CANCELLED' when design_id is not null then 'OPEN' end::layout.design_asset_state;
alter table layout.switch_version drop column cancelled;

alter table layout.switch
  add column design_asset_state layout.design_asset_state;
update layout.switch
set design_asset_state = case when cancelled then 'CANCELLED' when design_id is not null then 'OPEN' end::layout.design_asset_state;
alter table layout.switch
  add constraint switch_design_asset_state_in_design_check check ((design_asset_state is null) = (design_id is null));
alter table layout.switch drop column cancelled;


alter table layout.km_post_version
  add column design_asset_state layout.design_asset_state;
update layout.km_post_version
set design_asset_state = case when cancelled then 'CANCELLED' when design_id is not null then 'OPEN' end::layout.design_asset_state;
alter table layout.km_post_version drop column cancelled;

alter table layout.km_post
  add column design_asset_state layout.design_asset_state;
update layout.km_post
set design_asset_state = case when cancelled then 'CANCELLED' when design_id is not null then 'OPEN' end::layout.design_asset_state;
alter table layout.km_post
  add constraint km_post_design_asset_state_in_design_check check ((design_asset_state is null) = (design_id is null));
alter table layout.km_post drop column cancelled;


alter table layout.track_number
  enable trigger version_row_trigger;
alter table layout.track_number
  enable trigger version_update_trigger;
alter table layout.reference_line
  enable trigger version_row_trigger;
alter table layout.reference_line
  enable trigger version_update_trigger;
alter table layout.location_track
  enable trigger version_row_trigger;
alter table layout.location_track
  enable trigger version_update_trigger;
alter table layout.switch
  enable trigger version_row_trigger;
alter table layout.switch
  enable trigger version_update_trigger;
alter table layout.km_post
  enable trigger version_row_trigger;
alter table layout.km_post
  enable trigger version_update_trigger;
