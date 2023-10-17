create table common.location_track_owner
(
  id   int primary key generated always as identity,
  name varchar(100) not null
);

select common.add_table_metadata('common', 'location_track_owner');
comment on table common.location_track_owner is 'Location track owners';
