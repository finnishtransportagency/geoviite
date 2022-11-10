create type layout.track_type as enum (
  'MAIN',
  'SIDE',
  'TRAP',
  'CHORD'
  );

create type layout.track_topological_connectivity_type as enum (
  'NONE',
  'START',
  'END',
  'START_AND_END'
  );

create table layout.location_track
(
  id                             int primary key generated always as identity,
  alignment_id                   int                         not null,
  alignment_version              int                         not null,
  track_number_id                int                         not null,
  external_id                    varchar(50)                 null,
  name                           varchar(50)                 not null,
  description                    varchar(256)                not null,
  type                           layout.track_type           null,
  state                          layout.state                not null,
  draft                          boolean                     not null,
  draft_of_location_track_id     int                         null,
  duplicate_of_location_track_id int                         null,
  topological_connectivity       layout.track_topological_connectivity_type not null
);

select common.add_metadata_columns('layout', 'location_track');
comment on table layout.location_track is 'Layout Location Track: Single track of the layout network';
