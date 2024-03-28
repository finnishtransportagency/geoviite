create type layout.operating_point_type as enum ('LP', 'LPO', 'OLP', 'SEIS', 'LVH');

create table layout.operating_point
(
  external_id     varchar(50)                   not null primary key,
  name            varchar(50)                   not null,
  abbreviation    varchar(50)                   not null,
  uic_code        varchar(50)                   not null,
  type            layout.operating_point_type   not null,
  location        postgis.geometry(Point, 3067) not null,
  track_number_id integer                       not null,
  update_time     timestamp with time zone      not null default now(),

  constraint operating_point_track_number_fk foreign key (track_number_id) references layout.track_number (id)
);

create index operating_point_location_ix on layout.operating_point using gist (location);
create index operating_point_update_time_ix on layout.operating_point (update_time);
