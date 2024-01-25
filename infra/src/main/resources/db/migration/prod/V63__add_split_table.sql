create type publication.bulk_transfer_state as
  enum ('PENDING', 'IN_PROGRESS', 'DONE', 'FAILED', 'TEMPORARY_FAILURE');

create table publication.split
(
  id                       int primary key generated always as identity,
  source_location_track_id int                             not null,
  bulk_transfer_state      publication.bulk_transfer_state not null,
  publication_id           int                             null,

  constraint split_publication_fkey
    foreign key (publication_id) references publication.publication (id),

  constraint split_source_location_track_fkey
    foreign key (source_location_track_id) references layout.location_track (id)
);

comment on table publication.split is 'Location track split status';

select common.add_metadata_columns('publication', 'split');
select common.add_table_versioning('publication', 'split');
