create type integrations.projektivelho_file_status as enum ('NOT_IM', 'IMPORTED', 'REJECTED', 'ACCEPTED');
create type integrations.projektivelho_search_status as enum ('WAITING', 'FETCHING', 'FINISHED', 'ERROR');
create type integrations.dictionary_entry_type as enum ('DOC_TYPE', 'FILE_STATE', 'CATEGORY', 'ASSET_GROUP');

create table integrations.projektivelho_search
(
  id          int primary key generated always as identity,
  status      integrations.projektivelho_search_status not null,
  token       varchar(50),
  valid_until timestamp with time zone not null
);

create table integrations.projektivelho_assignment
(
  id          int primary key generated always as identity,
  oid         varchar(50) not null,
  name        varchar(100) not null,
  state       varchar(100) not null,
  created_at  timestamp with time zone not null,
  modified    timestamp with time zone not null
);

create table integrations.projektivelho_project
(
  id          int primary key generated always as identity,
  oid         varchar(50) not null,
  name        varchar(100) not null,
  state       varchar(100) not null,
  created_at  timestamp with time zone not null,
  modified    timestamp with time zone not null
);

create table integrations.projektivelho_project_group
(
  id          int primary key generated always as identity,
  oid         varchar(50) not null,
  name        varchar(100) not null,
  state       varchar(100) not null,
  created_at  timestamp with time zone not null,
  modified    timestamp with time zone not null
);

create table integrations.projektivelho_dictionary
(
  id          int primary key generated always as identity,
  type        integrations.dictionary_entry_type not null,
  code        varchar(50) not null,
  name        varchar(100) not null
);

create table integrations.projektivelho_file_metadata
(
  id                int primary key generated always as identity,
  oid               varchar(50) not null,
  filename          varchar(100) not null,
  file_version      varchar(50) not null,
  description       varchar(150),
  file_state        int references integrations.projektivelho_dictionary(id) not null,
  doc_type          int references integrations.projektivelho_dictionary(id) not null,
  asset_group       int references integrations.projektivelho_dictionary(id) not null,
  category          int references integrations.projektivelho_dictionary(id) not null,
  file_change_time  timestamp with time zone not null,
  status            integrations.projektivelho_file_status not null,
  assignment        integrations.projektivelho_assignment,
  project           integrations.projektivelho_project,
  project_group     integrations.projektivelho_project_group
);
select common.add_metadata_columns('integrations', 'projektivelho_file_metadata');

create table integrations.projektivelho_file
(
  metadata_id int primary key references integrations.projektivelho_file_metadata(id),
  content     xml not null
);

comment on table integrations.projektivelho_search is 'Contains searches to ProjektiVelho.';
comment on table integrations.projektivelho_file_metadata is 'Contains metadata for all files fetched from Projektivelho.';
comment on table integrations.projektivelho_file is 'Contains all non-discarded files fetched from ProjektiVelho.';
