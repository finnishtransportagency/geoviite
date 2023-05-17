create type integrations.projektivelho_file_status as enum ('NOT_IM', 'IMPORTED', 'REJECTED', 'ACCEPTED');
create type integrations.projektivelho_search_status as enum ('WAITING', 'FETCHING', 'FINISHED', 'ERROR');
-- create type integrations.projektivelho_dictionary_type as enum ('DOC_TYPE', 'FILE_STATE', 'CATEGORY', 'ASSET_GROUP');

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
  id         int primary key generated always as identity,
  oid        varchar(50)              not null,
  name       varchar(100)             not null,
  state      varchar(100)             not null,
  created_at timestamp with time zone not null,
  modified   timestamp with time zone not null
);
comment on table integrations.projektivelho_assignment is 'ProjektiVelho assignment';
select common.add_metadata_columns('integrations', 'projektivelho_assignment');
select common.add_table_versioning('integrations', 'projektivelho_assignment');

create table integrations.projektivelho_project
(
  id         int primary key generated always as identity,
  oid        varchar(50)              not null,
  name       varchar(100)             not null,
  state      varchar(100)             not null,
  created_at timestamp with time zone not null,
  modified   timestamp with time zone not null
);
comment on table integrations.projektivelho_project is 'ProjektiVelho project';
select common.add_metadata_columns('integrations', 'projektivelho_project');
select common.add_table_versioning('integrations', 'projektivelho_project');

create table integrations.projektivelho_project_group
(
  id         int primary key generated always as identity,
  oid        varchar(50)              not null,
  name       varchar(100)             not null,
  state      varchar(100)             not null,
  created_at timestamp with time zone not null,
  modified   timestamp with time zone not null
);
comment on table integrations.projektivelho_project_group is 'ProjektiVelho project group';
select common.add_metadata_columns('integrations', 'projektivelho_project_group');
select common.add_table_versioning('integrations', 'projektivelho_project_group');

create table integrations.projektivelho_document_type
(
  code varchar(50) primary key not null,
  name varchar(100)            not null
);
comment on table integrations.projektivelho_document_type is 'Localized ProjektiVelho document types (dokumenttityyppi)';
select common.add_metadata_columns('integrations', 'projektivelho_document_type');
select common.add_table_versioning('integrations', 'projektivelho_document_type');

create table integrations.projektivelho_material_state
(
  code varchar(50) primary key not null,
  name varchar(100)            not null
);
comment on table integrations.projektivelho_material_state is 'Localized ProjektiVelho material states (ainestotila)';
select common.add_metadata_columns('integrations', 'projektivelho_material_state');
select common.add_table_versioning('integrations', 'projektivelho_material_state');

create table integrations.projektivelho_material_group
(
  code varchar(50) primary key not null,
  name varchar(100)            not null
);
comment on table integrations.projektivelho_material_group is 'Localized Projektivelho material group (aineistoryhmä) options';
select common.add_metadata_columns('integrations', 'projektivelho_material_group');
select common.add_table_versioning('integrations', 'projektivelho_material_group');

create table integrations.projektivelho_material_category
(
  code varchar(50) primary key not null,
  name varchar(100)            not null
);
comment on table integrations.projektivelho_material_category is 'Localized Projektivelho material category (ainestolaji) options';
select common.add_metadata_columns('integrations', 'projektivelho_material_category');
select common.add_table_versioning('integrations', 'projektivelho_material_category');

create table integrations.projektivelho_technics_field
(
  code varchar(50) primary key not null,
  name varchar(100)            not null
);
comment on table integrations.projektivelho_technics_field is 'Localized Projektivelho technics field (tekniikka-ala) options';
select common.add_metadata_columns('integrations', 'projektivelho_technics_field');
select common.add_table_versioning('integrations', 'projektivelho_technics_field');

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
  -- TODO: GVT-1860 These should be not-null
  projektivelho_assignment_id          int                                    null
    references integrations.projektivelho_assignment (id),
  projektivelho_project_id             int                                    null
    references integrations.projektivelho_project (id),
  projektivelho_project_group_id       int                                    null
    references integrations.projektivelho_project_group (id)
);
comment on table integrations.projektivelho_file_metadata is 'Hangling status & metadata for Projektivelho files';
select common.add_metadata_columns('integrations', 'projektivelho_file_metadata');
select common.add_table_versioning('integrations', 'projektivelho_file_metadata');

create table integrations.projektivelho_file
(
  projektivelho_file_metadata_id int primary key references integrations.projektivelho_file_metadata (id),
  content                        xml not null
);
comment on table integrations.projektivelho_file is 'File contents for non-discarded files, fetched from ProjektiVelho';
