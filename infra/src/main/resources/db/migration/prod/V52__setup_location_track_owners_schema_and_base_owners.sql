create table common.location_track_owner
(
  id   int primary key generated always as identity,
  name varchar(100) not null unique
);

select common.add_table_metadata('common', 'location_track_owner');
comment on table common.location_track_owner is 'Location track owners';

insert into common.location_track_owner(name) values ('Väylävirasto'), ('Ei tiedossa');

alter table layout.location_track
  add column owner_id int references common.location_track_owner (id);

alter table layout.location_track_version
  add column owner_id int references common.location_track_owner (id);
