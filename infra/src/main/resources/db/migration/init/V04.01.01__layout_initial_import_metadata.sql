create table layout.initial_import_metadata
(
  id                    int primary key generated always as identity,
  alignment_external_id varchar(50)  not null,
  metadata_external_id  varchar(50)  null,
  track_address_start   varchar(20)  not null,
  track_address_end     varchar(20)  not null,
  measurement_method    varchar(50)  not null,
  plan_file_name        varchar(100) not null,
  plan_alignment_name   varchar(50)  not null,
  created_year          int          not null,
  original_crs          varchar(10)  not null,
  geometry_alignment_id int          null
);

comment on table layout.initial_import_metadata is 'Contains original metadata for alignments, from initial data import.';

create table layout.initial_segment_metadata
(
  alignment_id  int not null,
  segment_index int not null,
  metadata_id   int not null references layout.initial_import_metadata (id),


  primary key (alignment_id, segment_index)
);

comment on table layout.initial_segment_metadata is 'Links initial import-created segments to the metadata they were created with.';
