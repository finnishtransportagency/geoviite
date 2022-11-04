create table publication.publication
(
  id               int primary key generated always as identity,
  publication_user varchar(30) not null constraint publication_user_non_empty check (length(publication_user) > 1),
  publication_time timestamptz not null
);
comment on table publication.publication is 'Collection for the content of a single track layout publishing operation.';

create table publication.track_number
(
  publication_id       int not null references publication.publication (id),
  track_number_id      int not null references layout.track_number (id),
  track_number_version int not null,

  primary key (publication_id, track_number_id),
  constraint publication_track_number_id_fkey
    foreign key (track_number_id, track_number_version) references layout.track_number_version (id, version)
);
comment on table publication.track_number is 'Publication content reference for track number.';

create table publication.reference_line
(
  publication_id         int not null references publication.publication (id),
  reference_line_id      int not null references layout.reference_line (id),
  reference_line_version int not null,

  primary key (publication_id, reference_line_id),
  constraint publication_reference_line_id_fkey
    foreign key (reference_line_id, reference_line_version) references layout.reference_line_version (id, version)
);
comment on table publication.reference_line is 'Publication content reference for Reference Line.';

create table publication.location_track
(
  publication_id         int not null references publication.publication (id),
  location_track_id      int not null references layout.location_track (id),
  location_track_version int not null,

  primary key (publication_id, location_track_id),
  constraint publication_location_track_id_fkey
    foreign key (location_track_id, location_track_version) references layout.location_track_version (id, version)
);
comment on table publication.location_track is 'Publication content reference for Location Track.';

create table publication.switch
(
  publication_id int not null references publication.publication (id),
  switch_id      int not null references layout.switch (id),
  switch_version int not null,

  primary key (publication_id, switch_id),
  constraint publication_switch_track_id_fkey
    foreign key (switch_id, switch_version) references layout.switch_version (id, version)
);
comment on table publication.switch is 'Publication content reference for switch.';

create table publication.km_post
(
  publication_id  int not null references publication.publication (id),
  km_post_id      int not null references layout.km_post (id),
  km_post_version int not null,

  primary key (publication_id, km_post_id),
  constraint publication_km_post_id_fkey
    foreign key (km_post_id, km_post_version) references layout.km_post_version (id, version)
);
comment on table publication.km_post is 'Publication content reference for km-post.';
