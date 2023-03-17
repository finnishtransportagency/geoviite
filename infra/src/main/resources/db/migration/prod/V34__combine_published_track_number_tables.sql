--drop existing constraints
alter table publication.calculated_change_to_track_number
  drop constraint publication_cc_track_number_id_unique cascade;

alter table publication.calculated_change_to_track_number_km
  drop constraint calculated_change_to_track_number_km_publication_id_fkey,
  drop constraint calculated_change_to_track_number_km_track_number_id_fkey;

--rename existing constraints
alter table publication.calculated_change_to_track_number
  rename constraint calculated_change_to_track_number_publication_id_fkey to publication_track_number_publication_fk;

alter table publication.calculated_change_to_track_number
  rename constraint calculated_change_to_track_number_track_number_id_fkey to publication_track_number_track_number_fk;

alter table publication.km_post
  rename constraint km_post_pkey to publication_km_post_pk;

alter table publication.km_post
  rename constraint km_post_km_post_id_fkey to publication_km_post_km_post_fk;

alter table publication.km_post
  rename constraint km_post_publication_id_fkey to publication_km_post_publication_fk;

alter table publication.km_post
  rename constraint publication_km_post_id_fkey to publication_km_post_km_post_version_fk;

alter table publication.reference_line
  rename constraint reference_line_pkey to publication_reference_line_pk;

alter table publication.reference_line
  rename constraint reference_line_reference_line_id_fkey to publication_reference_line_reference_line_fk;

alter table publication.reference_line
  rename constraint reference_line_publication_id_fkey to publication_reference_line_publication_fk;

alter table publication.reference_line
  rename constraint publication_reference_line_id_fkey to publication_reference_line_reference_line_version_fk;

--migrate direct changes
alter table publication.calculated_change_to_track_number
  add column track_number_version int     null,
  add column direct_change        boolean null;

update publication.calculated_change_to_track_number
set track_number_version = track_number.version
  from publication.publication
    inner join layout.track_number_at(publication_time) track_number on true
  where publication.id = calculated_change_to_track_number.publication_id
    and track_number.id = calculated_change_to_track_number.track_number_id;

update publication.calculated_change_to_track_number
set
  direct_change = tn.track_number_id is not null
  from publication.calculated_change_to_track_number cctn
    left join publication.track_number tn
              on tn.track_number_id = cctn.track_number_id and tn.publication_id = cctn.publication_id
  where calculated_change_to_track_number.publication_id = cctn.publication_id
    and calculated_change_to_track_number.track_number_id = cctn.track_number_id;

alter table publication.calculated_change_to_track_number
  alter column direct_change set not null,
  alter column track_number_version set not null;

--rename existing tables
drop table publication.track_number;

alter table publication.calculated_change_to_track_number
  rename to track_number;

comment on table publication.track_number is 'Publication content reference for track number.';

alter table publication.calculated_change_to_track_number_km
  rename to track_number_km;

comment on table publication.track_number_km is 'Changed kilometers for published track numbers.';

--add new constraints
alter table publication.track_number
  add constraint publication_track_number_pk primary key (publication_id, track_number_id),
  add constraint publication_track_number_track_number_version_fk foreign key (track_number_id, track_number_version)
    references layout.track_number_version (id, version);

alter table publication.track_number_km
  add constraint publication_track_number_km_unique unique (publication_id, track_number_id, km_number),
  add constraint publication_track_number_km_publication_track_number
    foreign key (publication_id, track_number_id) references publication.track_number (publication_id, track_number_id);
