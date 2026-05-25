create type publication.split_administrative_change_type as enum ('SPLIT', 'BOUNDARY_CHANGE');

alter table publication.split
  disable trigger version_update_trigger;
alter table publication.split
  disable trigger version_row_trigger;

alter table publication.split_version
  add column administrative_change_type publication.split_administrative_change_type not null default 'SPLIT';
alter table publication.split_version
  alter column administrative_change_type drop default;

alter table publication.split
  add column administrative_change_type publication.split_administrative_change_type not null default 'SPLIT';
alter table publication.split
  alter column administrative_change_type drop default;

alter table publication.split
  enable trigger version_update_trigger;
alter table publication.split
  enable trigger version_row_trigger;
