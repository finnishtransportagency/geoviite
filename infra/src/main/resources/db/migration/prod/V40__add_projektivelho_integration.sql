create type integrations.projektivelho_file_status as enum ('NOT_IM', 'SUGGESTED', 'REJECTED', 'ACCEPTED');
create type integrations.projektivelho_search_status as enum ('WAITING', 'FETCHING', 'FINISHED', 'ERROR');

create table integrations.projektivelho_search
(
  id          int primary key generated always as identity,
  status      integrations.projektivelho_search_status not null,
  token       varchar(50)                              not null,
  valid_until timestamp with time zone                 not null
);
comment on table integrations.projektivelho_search is 'Status for ProjektiVelho asynchronous search operations';
select common.add_metadata_columns('integrations', 'projektivelho_search');
select common.add_table_versioning('integrations', 'projektivelho_search');

create table integrations.projektivelho_assignment
(
  oid        varchar(50)              not null primary key,
  name       varchar(100)             not null,
  state      varchar(100)             not null
    references integrations.projektivelho_project_state (code),
  created_at timestamp with time zone not null,
  modified   timestamp with time zone not null
);
comment on table integrations.projektivelho_assignment is 'ProjektiVelho assignment';
select common.add_metadata_columns('integrations', 'projektivelho_assignment');
select common.add_table_versioning('integrations', 'projektivelho_assignment');

create table integrations.projektivelho_project
(
  oid        varchar(50)              not null primary key,
  name       varchar(100)             not null,
  state      varchar(100)             not null
    references integrations.projektivelho_project_state (code),
  created_at timestamp with time zone not null,
  modified   timestamp with time zone not null
);
comment on table integrations.projektivelho_project is 'ProjektiVelho project';
select common.add_metadata_columns('integrations', 'projektivelho_project');
select common.add_table_versioning('integrations', 'projektivelho_project');

create table integrations.projektivelho_project_group
(
  oid        varchar(50)              not null primary key,
  name       varchar(100)             not null,
  state      varchar(100)             not null
    references integrations.projektivelho_project_state (code),
  created_at timestamp with time zone not null,
  modified   timestamp with time zone not null
);
comment on table integrations.projektivelho_project_group is 'ProjektiVelho project group';
select common.add_metadata_columns('integrations', 'projektivelho_project_group');
select common.add_table_versioning('integrations', 'projektivelho_project_group');

create table integrations.projektivelho_file_metadata
(
  id                                   int primary key generated always as identity,
  oid                                  varchar(50) unique                     not null,
  filename                             varchar(100)                           not null,
  file_version                         varchar(50)                            not null,
  description                          varchar(150),
  projektivelho_document_type_code     varchar(50)                            not null
    references integrations.projektivelho_document_type (code),
  projektivelho_material_state_code    varchar(50)                            not null
    references integrations.projektivelho_material_state (code),
  projektivelho_material_group_code    varchar(50)                            not null
    references integrations.projektivelho_material_group (code),
  projektivelho_material_category_code varchar(50)                            not null
    references integrations.projektivelho_material_category (code),
  file_change_time                     timestamp with time zone               not null,
  status                               integrations.projektivelho_file_status not null,
  projektivelho_assignment_oid         varchar(50)                            null
    references integrations.projektivelho_assignment (oid),
  projektivelho_project_oid            varchar(50)                            null
    references integrations.projektivelho_project (oid),
  projektivelho_project_group_oid      varchar(50)                            null
    references integrations.projektivelho_project_group (oid)
);
comment on table integrations.projektivelho_file_metadata is 'Handling status & metadata for Projektivelho files';
select common.add_metadata_columns('integrations', 'projektivelho_file_metadata');
select common.add_table_versioning('integrations', 'projektivelho_file_metadata');

create table integrations.projektivelho_file
(
  projektivelho_file_metadata_id int primary key references integrations.projektivelho_file_metadata (id),
  content                        xml not null
);
comment on table integrations.projektivelho_file is 'File contents for non-discarded files, fetched from ProjektiVelho';
