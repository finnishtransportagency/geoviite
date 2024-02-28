create table publication.split_updated_duplicate
(
  split_id     int not null,
  duplicate_id int not null,

  primary key (split_id, duplicate_id),

  constraint split_fkey foreign key (split_id) references publication.split (id) on delete cascade,
  constraint split_duplicate_fkey foreign key (duplicate_id) references layout.location_track (id)
);

comment on table publication.split_relinked_switch is 'Duplicate location tracks that were updated during a location track split';

select common.add_metadata_columns('publication', 'split_updated_duplicate');
select common.add_table_versioning('publication', 'split_updated_duplicate');
