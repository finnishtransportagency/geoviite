alter table layout.location_track disable trigger version_update_trigger;
alter table layout.location_track disable trigger version_row_trigger;

alter table layout.location_track
    add column owner_id int references common.location_track_owner(id);

alter table layout.location_track_version
    add column owner_id int references common.location_track_owner(id);

update layout.location_track
set owner_id = (select id from common.location_track_owner where name = 'Väylävirasto' LIMIT 1);

update layout.location_track_version
set owner_id = (select id from common.location_track_owner where name = 'Väylävirasto' LIMIT 1);

alter table layout.location_track
    alter column owner_id set not null;
alter table layout.location_track_version
    alter column owner_id set not null;

alter table layout.location_track enable trigger version_update_trigger;
alter table layout.location_track enable trigger version_row_trigger;
