create type layout.location_track_description_suffix as enum (
  'SWITCH_TO_SWITCH',
  'SWITCH_TO_BUFFER',
  'NONE'
);

alter table layout.location_track disable trigger version_update_trigger;
alter table layout.location_track disable trigger version_row_trigger;

alter table layout.location_track_version rename column description to description_base;
alter table layout.location_track_version add column description_suffix layout.location_track_description_suffix not null default 'NONE';
alter table layout.location_track rename column description to description_base;
alter table layout.location_track add column description_suffix layout.location_track_description_suffix not null default 'NONE';

alter table layout.location_track enable trigger version_row_trigger;
alter table layout.location_track enable trigger version_update_trigger;
