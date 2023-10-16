alter table layout.location_track add column owner_id int references common.location_track_owner(id);
alter table layout.location_track_version add column owner_id int references common.location_track_owner(id);
