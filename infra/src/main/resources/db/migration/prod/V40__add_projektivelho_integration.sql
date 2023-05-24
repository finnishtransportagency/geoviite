create type projektivelho.file_status as enum ('NOT_IM', 'SUGGESTED', 'REJECTED', 'ACCEPTED');
create type projektivelho.search_status as enum ('WAITING', 'FETCHING', 'FINISHED', 'ERROR');

create table projektivelho.search
(
  id          int primary key generated always as identity,
  status      projektivelho.search_status not null,
  token       varchar(50)                 not null,
  valid_until timestamp with time zone    not null
);
comment on table projektivelho.search is 'Status for ProjektiVelho asynchronous search operations';
select common.add_metadata_columns('projektivelho', 'search');
select common.add_table_versioning('projektivelho', 'search');

create table projektivelho.assignment
(
  oid        varchar(50)              not null primary key,
  name       varchar(100)             not null,
  state_code varchar(100)             not null references projektivelho.project_state (code),
  created_at timestamp with time zone not null,
  modified   timestamp with time zone not null
);
comment on table projektivelho.assignment is 'ProjektiVelho assignment';
select common.add_metadata_columns('projektivelho', 'assignment');
select common.add_table_versioning('projektivelho', 'assignment');

create table projektivelho.project
(
  oid        varchar(50)              not null primary key,
  name       varchar(100)             not null,
  state_code varchar(100)             not null references projektivelho.project_state (code),
  created_at timestamp with time zone not null,
  modified   timestamp with time zone not null
);
comment on table projektivelho.project is 'ProjektiVelho project';
select common.add_metadata_columns('projektivelho', 'project');
select common.add_table_versioning('projektivelho', 'project');

create table projektivelho.project_group
(
  oid        varchar(50)              not null primary key,
  name       varchar(100)             not null,
  state_code varchar(100)             not null references projektivelho.project_state (code),
  created_at timestamp with time zone not null,
  modified   timestamp with time zone not null
);
comment on table projektivelho.project_group is 'ProjektiVelho project group';
select common.add_metadata_columns('projektivelho', 'project_group');
select common.add_table_versioning('projektivelho', 'project_group');

create table projektivelho.file_metadata
(
  id                     int primary key generated always as identity,
  oid                    varchar(50) unique        not null,
  filename               varchar(100)              not null,
  file_version           varchar(50)               not null,
  description            varchar(150),
  document_type_code     varchar(50)               not null references projektivelho.document_type (code),
  material_state_code    varchar(50)               not null references projektivelho.material_state (code),
  material_group_code    varchar(50)               not null references projektivelho.material_group (code),
  material_category_code varchar(50)               not null references projektivelho.material_category (code),
  file_change_time       timestamp with time zone  not null,
  status                 projektivelho.file_status not null,
  assignment_oid         varchar(50)               null references projektivelho.assignment (oid),
  project_oid            varchar(50)               null references projektivelho.project (oid),
  project_group_oid      varchar(50)               null references projektivelho.project_group (oid)
);
comment on table projektivelho.file_metadata is 'Handling status & metadata for Projektivelho files';
select common.add_metadata_columns('projektivelho', 'file_metadata');
select common.add_table_versioning('projektivelho', 'file_metadata');

create table projektivelho.file
(
  file_metadata_id int primary key references projektivelho.file_metadata (id),
  content          xml not null
);
comment on table projektivelho.file is 'File contents for non-discarded files, fetched from ProjektiVelho';
