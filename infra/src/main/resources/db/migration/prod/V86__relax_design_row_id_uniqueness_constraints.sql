alter table layout.track_number
  drop constraint track_number_design_row_id_unique,
  add constraint track_number_design_row_id_unique_in_designs exclude (design_row_id with =) where (design_row_id is not null and design_id is not null),
  add constraint track_number_design_row_id_unique_in_main exclude (design_row_id with =) where (design_row_id is not null and design_id is null),
  add constraint track_number_only_draft_has_design_row check (draft or design_row_id is null);

alter table layout.reference_line
  drop constraint reference_line_design_row_id_unique,
  add constraint reference_line_design_row_id_unique_in_designs exclude (design_row_id with =) where (design_row_id is not null and design_id is not null),
  add constraint reference_line_design_row_id_unique_in_main exclude (design_row_id with =) where (design_row_id is not null and design_id is null),
  add constraint reference_line_only_draft_has_design_row check (draft or design_row_id is null);

alter table layout.location_track
  drop constraint location_track_design_row_id_unique,
  add constraint location_track_design_row_id_unique_in_designs exclude (design_row_id with =) where (design_row_id is not null and design_id is not null),
  add constraint location_track_design_row_id_unique_in_main exclude (design_row_id with =) where (design_row_id is not null and design_id is null),
  add constraint location_track_only_draft_has_design_row check (draft or design_row_id is null);

alter table layout.switch
  drop constraint switch_design_row_id_unique,
  add constraint switch_design_row_id_unique_in_designs exclude (design_row_id with =) where (design_row_id is not null and design_id is not null),
  add constraint switch_design_row_id_unique_in_main exclude (design_row_id with =) where (design_row_id is not null and design_id is null),
  add constraint switch_only_draft_has_design_row check (draft or design_row_id is null);

alter table layout.km_post
  drop constraint km_post_design_row_id_unique,
  add constraint km_post_design_row_id_unique_in_designs exclude (design_row_id with =) where (design_row_id is not null and design_id is not null),
  add constraint km_post_design_row_id_unique_in_main exclude (design_row_id with =) where (design_row_id is not null and design_id is null),
  add constraint km_post_only_draft_has_design_row check (draft or design_row_id is null);
