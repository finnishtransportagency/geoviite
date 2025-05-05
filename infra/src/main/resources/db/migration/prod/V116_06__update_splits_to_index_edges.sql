alter table publication.split_target_location_track
  disable trigger version_update_trigger,
  disable trigger version_row_trigger;

alter table publication.split_target_location_track
  add column source_start_edge_index int null,
  add column source_end_edge_index int null;

alter table publication.split_target_location_track_version
  add column source_start_edge_index int null,
  add column source_end_edge_index int null;

-- We could update existing data here, but there shouldn't be any yet. The rest will fail if there is

alter table publication.split_target_location_track
  alter column source_start_edge_index set not null,
  alter column source_end_edge_index set not null,
  drop column source_start_segment_index,
  drop column source_end_segment_index;

alter table publication.split_target_location_track_version
  alter column source_start_edge_index set not null,
  alter column source_end_edge_index set not null,
  drop column source_start_segment_index,
  drop column source_end_segment_index;

alter table publication.split_target_location_track
  enable trigger version_update_trigger,
  enable trigger version_row_trigger;
