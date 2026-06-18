-- No longer relevant: layout.reference_line_version table was merged into layout.track_number_version in V149_03
drop index if exists layout.reference_line_version_track_number_id_change_time_ix;
