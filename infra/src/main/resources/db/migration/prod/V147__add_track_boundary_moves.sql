create table publication.track_boundary_move
(
  id                                          integer primary key generated always as identity,
  shortened_location_track_id                 integer not null,
  shortened_location_track_version            integer not null,
  shortened_location_track_layout_context_id  text    not null,
  source_start_edge_index                     integer not null,
  source_end_edge_index                       integer not null,
  lengthened_location_track_id                integer not null,
  lengthened_location_track_version           integer not null,
  lengthened_location_track_layout_context_id text    not null,
  publication_id                              integer,

  constraint track_boundary_move_shortened_track_version_fk
    foreign key (shortened_location_track_id, shortened_location_track_version,
                 shortened_location_track_layout_context_id)
      references layout.location_track_version (id, version, layout_context_id),
  constraint track_boundary_move_shortened_track_start_edge_fk
    foreign key (shortened_location_track_id, shortened_location_track_version,
                 shortened_location_track_layout_context_id,
                 source_start_edge_index)
      references layout.location_track_version_edge (location_track_id, location_track_version,
                                                     location_track_layout_context_id, edge_index),
  constraint track_boundary_move_shortened_track_end_edge_fk
    foreign key (shortened_location_track_id, shortened_location_track_version,
                 shortened_location_track_layout_context_id,
                 source_end_edge_index)
      references layout.location_track_version_edge (location_track_id, location_track_version,
                                                     location_track_layout_context_id, edge_index),
  constraint track_boundary_move_lengthened_track_version_fk
    foreign key (lengthened_location_track_id, lengthened_location_track_version,
                 lengthened_location_track_layout_context_id)
      references layout.location_track_version (id, version, layout_context_id),
  constraint track_boundary_move_publication_fk
    foreign key (publication_id) references publication.publication
);

select common.add_metadata_columns('publication', 'track_boundary_move');
select common.add_table_versioning('publication', 'track_boundary_move');


