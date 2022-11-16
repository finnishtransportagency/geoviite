alter table layout.switch
  add constraint switch_id_version_unique unique (id, version),
  add constraint switch_geometry_switch_id_fkey foreign key (geometry_switch_id) references geometry.switch (id),
  add constraint switch_switch_structure_id_fkey foreign key (switch_structure_id) references common.switch_structure (id),
  add constraint switch_owner_id_fkey foreign key (owner_id) references common.switch_owner (id),
  add constraint switch_draft_of_switch_id_fkey foreign key (draft_of_switch_id) references layout.switch (id),
  add constraint switch_external_id_unique unique (external_id, draft),
  add constraint switch_draft_of_switch_id_unique unique (draft_of_switch_id);
select common.add_table_versioning('layout', 'switch');

alter table layout.switch_joint
  add constraint switch_joint_switch_id_fkey foreign key (switch_id, switch_version) references layout.switch (id, version) on delete cascade deferrable initially deferred;
select common.add_table_versioning('layout', 'switch_joint');

alter table layout.km_post
  add constraint km_post_track_number_id_fkey foreign key (track_number_id) references layout.track_number (id),
  add constraint km_post_geometry_km_post_id_fkey foreign key (geometry_km_post_id) references geometry.km_post (id),
  add constraint km_post_draft_of_km_post_id_fkey foreign key (draft_of_km_post_id) references layout.km_post (id),
  add constraint km_post_track_number_km_unique unique (track_number_id, km_number, draft),
  add constraint km_post_draft_of_alignment_id_unique unique (draft_of_km_post_id);
select common.add_table_versioning('layout', 'km_post');

alter table layout.alignment
  add constraint alignment_id_version_unique unique (id, version),
  add constraint alignment_geometry_alignment_id_fkey foreign key (geometry_alignment_id) references geometry.alignment (id);
select common.add_table_versioning('layout', 'alignment');

alter table layout.segment
  add column bounding_box postgis.geometry generated always as (postgis.st_envelope(geometry)) stored,
  add constraint segment_alignment_id_fkey foreign key (alignment_id, alignment_version) references layout.alignment (id, version) on delete cascade deferrable initially deferred,
  add constraint segment_switch_id_fkey foreign key (switch_id) references layout.switch (id),
  add constraint segment_geometry_alignment_id_fkey foreign key (geometry_alignment_id) references geometry.alignment (id),
  add constraint segment_geometry_alignment_id_geometry_element_index_fkey foreign key (geometry_alignment_id, geometry_element_index) references geometry.element (alignment_id, element_index);
select common.add_table_versioning('layout', 'segment');

alter table layout.location_track
  add constraint location_track_external_id_unique unique (external_id, draft),
  add constraint location_track_draft_of_location_track_id_unique unique (draft_of_location_track_id),
  add constraint location_track_track_number_id_fkey foreign key (track_number_id) references layout.track_number (id),
  add constraint location_track_alignment_fkey foreign key (alignment_id, alignment_version) references layout.alignment (id, version) deferrable initially deferred,
  add constraint location_track_draft_of_location_track_id_fkey foreign key (draft_of_location_track_id) references layout.location_track (id),
  add constraint location_track_duplicate_of_location_track_id_fkey foreign key (duplicate_of_location_track_id) references layout.location_track (id),
  add constraint location_track_topology_start_switch_id_fkey foreign key (topology_start_switch_id) references layout.switch (id),
  add constraint location_track_topology_end_switch_id_fkey foreign key (topology_end_switch_id) references layout.switch (id);
select common.add_table_versioning('layout', 'location_track');

alter table layout.reference_line
  add constraint reference_line_track_number_unique unique (track_number_id, draft),
  add constraint reference_line_draft_of_reference_line_id_unique unique (draft_of_reference_line_id),
  add constraint reference_line_track_number_id_fkey foreign key (track_number_id) references layout.track_number (id),
  add constraint reference_line_alignment_id_fkey foreign key (alignment_id, alignment_version) references layout.alignment (id, version) deferrable initially deferred,
  add constraint reference_line_draft_of_reference_line_id_fkey foreign key (draft_of_reference_line_id) references layout.reference_line (id);
select common.add_table_versioning('layout', 'reference_line');
