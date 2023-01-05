create index segment_version_geometry_alignment_version_ix on layout.segment_version (geometry_alignment_id, version);
create index location_track_version_alignment_id_version_ix on layout.location_track_version (alignment_id, alignment_version);
create index reference_line_version_alignment_id_version_ix on layout.reference_line_version (alignment_id, alignment_version);
create index switch_version_geometry_switch_version_ix on layout.switch_version (geometry_switch_id, version);
create index km_post_version_geometry_km_post_version_ix on layout.km_post_version (geometry_km_post_id, version);

