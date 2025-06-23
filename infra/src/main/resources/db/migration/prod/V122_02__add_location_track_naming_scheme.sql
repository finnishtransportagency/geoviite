alter table layout.location_track
  disable trigger version_update_trigger;
alter table layout.location_track
  disable trigger version_row_trigger;

create type layout.location_track_naming_scheme as enum (
  'FREE_TEXT',
  'WITHIN_OPERATING_POINT',
  'BETWEEN_OPERATING_POINTS',
  'TRACK_NUMBER_TRACK',
  'CHORD'
  );

create type layout.location_track_specifier as enum (
  'PR',
  'ER',
  'IR',
  'KR',
  'LR',
  'PSR',
  'ESR',
  'ISR',
  'LSR',
  'PKR',
  'EKR',
  'IKR',
  'LKR',
  'ITHR',
  'LANHR'
  );

alter table layout.location_track_version
  add column naming_scheme layout.location_track_naming_scheme not null default 'FREE_TEXT';
alter table layout.location_track_version
  add column name_specifier layout.location_track_specifier;
alter table layout.location_track_version
  add column name_free_text varchar(50) not null default '';
update layout.location_track_version set name_free_text = name;

alter table layout.location_track
  add column naming_scheme layout.location_track_naming_scheme not null default 'FREE_TEXT';
alter table layout.location_track
  add column name_specifier layout.location_track_specifier;
alter table layout.location_track
  add column name_free_text varchar(50) not null default '';
update layout.location_track set name_free_text = name;

alter table layout.location_track
  enable trigger version_update_trigger;
alter table layout.location_track
  enable trigger version_row_trigger;