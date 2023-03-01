--drop existing constraints
alter table publication.calculated_change_to_switch
  drop constraint publication_cc_switch_id_unique cascade;

alter table publication.calculated_change_to_switch_joint
  drop constraint calculated_change_to_switch_joint_publication_id_fkey,
  drop constraint calculated_change_to_switch_joint_switch_id_fkey;

alter table publication.switch_location_tracks
  drop constraint switch_location_tracks_publication_id_fkey,
  drop constraint switch_location_tracks_switch_id_fkey;

--rename existing constraints
alter table publication.calculated_change_to_switch
  rename constraint calculated_change_to_switch_publication_id_fkey to publication_switch_publication_fk;

alter table publication.calculated_change_to_switch
  rename constraint calculated_change_to_switch_switch_id_fkey to publication_switch_switch_fk;

alter table publication.calculated_change_to_switch
  rename constraint calculated_change_to_switch_version_fk to publication_switch_switch_version_fk;

alter table publication.calculated_change_to_switch_joint
  rename constraint calculated_change_to_switch_joint_location_track_id_fkey
    to publication_switch_joint_location_track_fk;

alter table publication.calculated_change_to_switch_joint
  rename constraint calculated_change_to_switch_joint_track_number_id_fkey
    to publication_switch_joint_track_number_fk;

alter table publication.switch_location_tracks
  rename constraint publication_switch_location_track_id_fkey
    to publication_switch_location_tracks_location_track_version_fk;

alter table publication.switch_location_tracks
  rename constraint switch_location_tracks_location_track_id_fkey
    to publication_switch_location_tracks_location_track_fk;

--migrate direct changes
alter table publication.calculated_change_to_switch
  add column direct_change boolean null;

update publication.calculated_change_to_switch
set direct_change = switch.switch_id is not null
  from publication.calculated_change_to_switch ccs
    left join publication.switch
              on switch.switch_id = ccs.switch_id
                and switch.publication_id = ccs.publication_id
  where calculated_change_to_switch.publication_id = ccs.publication_id
    and calculated_change_to_switch.switch_id = ccs.switch_id;

alter table publication.calculated_change_to_switch
  alter column direct_change set not null;

--rename existing tables
drop table publication.switch;

alter table publication.calculated_change_to_switch
  rename to switch;

comment on table publication.switch is 'Publication content reference for switch.';

alter table publication.calculated_change_to_switch_joint
  rename to switch_joint;

comment on table publication.switch_joint is 'Changed switch joints for published switches.';

--add new constraints
alter table publication.switch
  add constraint publication_switch_pk primary key (publication_id, switch_id);

alter table publication.switch_joint
  add constraint publication_switch_joint_switch_fk
    foreign key (publication_id, switch_id) references publication.switch (publication_id, switch_id);

alter table publication.switch_location_tracks
  add constraint publication_switch_location_tracks unique (publication_id, switch_id, location_track_id),
  add constraint publication_switch_location_tracks_publication_switch_fk
    foreign key (publication_id, switch_id) references publication.switch (publication_id, switch_id);
