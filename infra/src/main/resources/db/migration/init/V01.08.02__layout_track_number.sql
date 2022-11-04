create table layout.track_number
(
  id                       int primary key generated always as identity,
  external_id              varchar(50)    null,
  number                   varchar(30)    not null,
  description              varchar(100)   not null,
  state                    layout.state   not null,
  draft                    boolean        not null,
  draft_of_track_number_id int            null references layout.track_number (id),

  constraint track_number_number_draft_unique unique (number, draft),
  constraint track_number_external_id_draft_unique unique (external_id, draft),
  constraint track_number_draft_of_track_number_id_unique unique (draft_of_track_number_id)
);

select common.add_table_metadata('layout', 'track_number');
comment on table layout.track_number is 'Layout track number.';
