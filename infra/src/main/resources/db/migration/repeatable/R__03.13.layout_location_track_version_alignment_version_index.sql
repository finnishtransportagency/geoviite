drop index if exists layout.location_track_version_alignment_id_version_ix;
create index location_track_version_alignment_id_version_ix on layout.location_track_version (alignment_id, alignment_version);
