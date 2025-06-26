alter table layout.location_track
  disable trigger version_update_trigger,
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

-- Add columns as null initially
alter table layout.location_track_version
  add column naming_scheme  layout.location_track_naming_scheme null,
  add column name_specifier layout.location_track_specifier     null,
  add column name_free_text varchar(50)                         null;
alter table layout.location_track
  add column naming_scheme  layout.location_track_naming_scheme null,
  add column name_specifier layout.location_track_specifier     null,
  add column name_free_text varchar(50)                         null;

-- Migrate data in version table into the new structured format as "FREE_TEXT"
update layout.location_track_version
set
  naming_scheme = 'FREE_TEXT',
  name_free_text = name;

-- Copy migrated data from version table to active table
update layout.location_track lt
set
  name_free_text = v.name_free_text,
  naming_scheme = v.naming_scheme,
  name_specifier = v.name_specifier
  from layout.location_track_version v
  where v.id = lt.id
    and v.layout_context_id = lt.layout_context_id
    and v.version = lt.version;

-- Add not null constraints & enable triggers
alter table layout.location_track_version
  alter column naming_scheme set not null;
alter table layout.location_track
  alter column naming_scheme set not null,
  enable trigger version_update_trigger,
  enable trigger version_row_trigger;
