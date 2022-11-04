create table layout.reference_line
(
  id                         int primary key generated always as identity,
  track_number_id            int            not null,
  alignment_id               int            not null,
  alignment_version          int            not null,
  start_address              varchar(20)    not null,
  draft                      boolean        not null,
  draft_of_reference_line_id int            null
);

select common.add_metadata_columns('layout', 'reference_line');
comment on table layout.reference_line is 'Reference line for track number, for calculating addresses';
