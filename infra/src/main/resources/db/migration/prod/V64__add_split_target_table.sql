create table publication.split_target_location_track
(
  split_id                   int not null,
  location_track_id          int not null,
  source_start_segment_index int not null,
  source_end_segment_index   int not null,

  primary key (split_id, location_track_id),

  constraint split_fkey foreign key (split_id) references publication.split (id) on delete cascade,
  constraint split_target_location_track_fkey foreign key (location_track_id) references layout.location_track (id)
);

comment on table publication.split_target_location_track is 'Location track split target: the new tracks born in a split';

select common.add_metadata_columns('publication', 'split_target_location_track');
select common.add_table_versioning('publication', 'split_target_location_track');
