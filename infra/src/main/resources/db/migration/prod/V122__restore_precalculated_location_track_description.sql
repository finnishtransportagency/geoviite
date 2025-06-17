alter table layout.location_track
  disable trigger version_update_trigger;
alter table layout.location_track
  disable trigger version_row_trigger;

alter table layout.location_track_version
  add column description varchar(256) not null default '';
alter table layout.location_track
  add column description varchar(256) not null default '';

-- TODO: GVT-3080
update layout.location_track_version set description = ...;

update layout.location_track t
set description = v.description
  from layout.location_track_version v
  where v.id = t.id
    and v.layout_context_id = t.layout_context_id
    and v.version = t.version;

alter table layout.location_track
  enable trigger version_update_trigger;
alter table layout.location_track
  enable trigger version_row_trigger;
