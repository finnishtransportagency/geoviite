create table publication.switch_location_tracks
(
  publication_id         int  not null references publication.publication (id),
  switch_id              int  not null references layout.switch (id),
  location_track_id      int  not null references layout.location_track (id),
  location_track_version int  not null,
  is_topology_switch     bool not null,

  constraint publication_switch_calculated_change_switch_fkey
    foreign key (publication_id, switch_id) references publication.calculated_change_to_switch (publication_id, switch_id),

  constraint publication_switch_location_track_id_fkey
    foreign key (location_track_id, location_track_version) references layout.location_track_version (id, version)
);

comment on table publication.switch_location_tracks is 'References to location tracks for published switches.';
