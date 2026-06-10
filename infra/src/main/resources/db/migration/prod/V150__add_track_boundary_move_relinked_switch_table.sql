create table publication.track_boundary_move_relinked_switch
(
  track_boundary_move_id int not null,
  switch_id              int not null,

  primary key (track_boundary_move_id, switch_id),

  constraint track_boundary_move_relinked_switch_move_fkey foreign key (track_boundary_move_id)
    references publication.track_boundary_move (id) on delete cascade,
  constraint track_boundary_move_relinked_switch_switch_fkey foreign key (switch_id) references layout.switch_id (id)
);

comment on table publication.track_boundary_move_relinked_switch is 'Switches that were re-linked during a track boundary move';

select common.add_metadata_columns('publication', 'track_boundary_move_relinked_switch');
select common.add_table_versioning('publication', 'track_boundary_move_relinked_switch');
