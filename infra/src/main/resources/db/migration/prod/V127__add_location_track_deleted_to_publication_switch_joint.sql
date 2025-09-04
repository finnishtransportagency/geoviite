alter table publication.switch_joint
  add column location_track_deleted boolean not null default false;
alter table publication.switch_joint
  alter column location_track_deleted drop default;
