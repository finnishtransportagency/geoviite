alter table publication.track_boundary_move
  add column design_id int,
  add constraint track_boundary_move_design_fk foreign key (design_id) references layout.design (id);

alter table publication.track_boundary_move_version
  add column design_id int;
