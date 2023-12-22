create table common.location_track_owner
(
  id   int primary key generated always as identity,
  name varchar(100) not null unique,
  sort_priority int not null default 2
);

select common.add_table_metadata('common', 'location_track_owner');
comment on table common.location_track_owner is 'Location track owners';

insert into common.location_track_owner(name, sort_priority)
  values
    ('Väylävirasto', 0),
    ('Väylävirasto / yksityinen', 0),
    ('Muu yksityinen', 1);

alter table layout.location_track
  add column owner_id int references common.location_track_owner (id);

alter table layout.location_track_version
  add column owner_id int references common.location_track_owner (id);
