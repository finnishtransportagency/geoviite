create table publication.calculated_change_to_track_number
(
  publication_id  int     not null references publication.publication (id),
  track_number_id int     not null references layout.track_number (id),
  start_changed   boolean not null,
  end_changed     boolean not null,

  constraint publication_cc_track_number_id_unique unique (publication_id, track_number_id)
);

create table publication.calculated_change_to_track_number_km
(
  publication_id  int                  not null references publication.publication (id),
  track_number_id int                  not null references layout.track_number (id),
  km_number       character varying(6) not null,

  constraint publication_cc_track_number_track_km_track_number_fkey
    foreign key (publication_id, track_number_id)
      references publication.calculated_change_to_track_number (publication_id, track_number_id)
);

create table publication.calculated_change_to_location_track
(
  publication_id    int     not null references publication.publication (id),
  location_track_id int     not null references layout.location_track (id),
  start_changed     boolean not null,
  end_changed       boolean not null,

  constraint publication_cc_location_track_id_unique unique (publication_id, location_track_id)
);

create table publication.calculated_change_to_location_track_km
(
  publication_id    int                  not null references publication.publication (id),
  location_track_id int                  not null references layout.location_track (id),
  km_number         character varying(6) not null,

  constraint publication_cc_location_track_km_location_track_fkey
    foreign key (publication_id, location_track_id)
      references publication.calculated_change_to_location_track (publication_id, location_track_id)
);

create table publication.calculated_change_to_switch
(
  publication_id int not null references publication.publication (id),
  switch_id      int not null references layout.switch (id),

  constraint publication_cc_switch_id_unique unique (publication_id, switch_id)
);

create table publication.calculated_change_to_switch_joint
(
  publication_id             int                           not null references publication.publication (id),
  switch_id                  int                           not null references layout.switch (id),
  joint_number               int                           not null,
  removed                    boolean                       not null,
  address                    character varying(20)         not null,
  point                      postgis.geometry(point, 3067) not null, -- SRID 3067 = ETRS89 / TM35FIN, aka LAYOUT_SRID
  location_track_id          int                           not null references layout.location_track (id),
  location_track_external_id varchar(100)                  not null,
  track_number_id            int                           not null references layout.track_number (id),
  track_number_external_id   varchar(100)                  not null,

  constraint publication_cc_switch_joint_change_track_switch_fkey
    foreign key (publication_id, switch_id)
      references publication.calculated_change_to_switch (publication_id, switch_id)
);
