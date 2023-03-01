--drop existing constraints
alter table publication.calculated_change_to_location_track
  drop constraint publication_cc_location_track_id_unique cascade;

alter table publication.calculated_change_to_location_track_km
  drop constraint calculated_change_to_location_track_km_location_track_id_fkey,
  drop constraint calculated_change_to_location_track_km_publication_id_fkey;

--rename existing constraints
alter table publication.calculated_change_to_location_track
  rename constraint calculated_change_to_location_track_publication_id_fkey
    to publication_location_track_publication_fk;

alter table publication.calculated_change_to_location_track
  rename constraint calculated_change_to_location_track_location_track_id_fkey
    to publication_location_track_location_track_fk;

--migrate direct changes and versions
alter table publication.calculated_change_to_location_track
  add column location_track_version int     null,
  add column direct_change          boolean null;

update publication.calculated_change_to_location_track
set location_track_version = location_track.version
  from publication.publication
    inner join layout.location_track_at(publication_time) location_track on true
  where publication.id = calculated_change_to_location_track.publication_id
    and location_track.id = calculated_change_to_location_track.location_track_id;

update publication.calculated_change_to_location_track
set direct_change = location_track.location_track_id is not null
  from publication.calculated_change_to_location_track cclt
    left join publication.location_track
              on location_track.location_track_id = cclt.location_track_id
                and location_track.publication_id = cclt.publication_id
  where calculated_change_to_location_track.publication_id = cclt.publication_id
    and calculated_change_to_location_track.location_track_id = cclt.location_track_id;

alter table publication.calculated_change_to_location_track
  alter column location_track_version set not null;

alter table publication.calculated_change_to_location_track
  alter column direct_change set not null;

--rename existing tables
drop table publication.location_track;

alter table publication.calculated_change_to_location_track
  rename to location_track;

comment on table publication.location_track is 'Publication content reference for location track.';

alter table publication.calculated_change_to_location_track_km
  rename to location_track_km;

comment on table publication.location_track_km is 'Changed kilometers for published location tracks.';

--add new constraints and indexes
alter table publication.location_track
  add constraint publication_location_track_pk primary key (publication_id, location_track_id),
  add constraint publication_location_track_location_track_version_fk
    foreign key (location_track_id, location_track_version) references layout.location_track_version (id, version);

alter table publication.location_track_km
  add constraint publication_location_track_km_publication_location_track_fk
    foreign key (publication_id, location_track_id) references publication.location_track (publication_id, location_track_id),
  add constraint publication_location_track_km_unique unique (publication_id, location_track_id, km_number);
