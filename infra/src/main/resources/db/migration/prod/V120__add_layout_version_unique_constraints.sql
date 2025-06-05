alter table layout.track_number
  add constraint track_number_id_version_unique unique (id, layout_context_id, version);
alter table layout.reference_line
  add constraint reference_line_id_version_unique unique (id, layout_context_id, version);
alter table layout.km_post
  add constraint km_post_id_version_unique unique (id, layout_context_id, version);
