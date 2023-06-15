create type projektivelho.document_status as enum ('NOT_IM', 'FETCH_ERROR', 'SUGGESTED', 'REJECTED', 'ACCEPTED');
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
  oid        varchar(50) primary key,
  name       varchar(200)             not null,
  state_code varchar(100)             not null references projektivelho.project_state (code),
  created_at timestamp with time zone not null,
  modified   timestamp with time zone not null
);
comment on table projektivelho.assignment is 'ProjektiVelho assignment';
select common.add_metadata_columns('projektivelho', 'assignment');
select common.add_table_versioning('projektivelho', 'assignment');

create table projektivelho.project
(
  oid        varchar(50) primary key,
  name       varchar(200)             not null,
  state_code varchar(100)             not null references projektivelho.project_state (code),
  created_at timestamp with time zone not null,
  modified   timestamp with time zone not null
);
comment on table projektivelho.project is 'ProjektiVelho project';
select common.add_metadata_columns('projektivelho', 'project');
select common.add_table_versioning('projektivelho', 'project');

create table projektivelho.project_group
(
  oid        varchar(50) primary key,
  name       varchar(200)             not null,
  state_code varchar(100)             not null references projektivelho.project_state (code),
  created_at timestamp with time zone not null,
  modified   timestamp with time zone not null
);
comment on table projektivelho.project_group is 'ProjektiVelho project group';
select common.add_metadata_columns('projektivelho', 'project_group');
select common.add_table_versioning('projektivelho', 'project_group');

create table projektivelho.document
(
  id                     int primary key generated always as identity,
  oid                    varchar(50) unique            not null,
  status                 projektivelho.document_status not null,
  filename               varchar(100)                  not null,
  description            varchar(500)                  null,
  document_version       varchar(50)                   not null,
  document_change_time   timestamp with time zone      not null,
  document_type_code     varchar(50)                   not null references projektivelho.document_type (code),
  material_state_code    varchar(50)                   not null references projektivelho.material_state (code),
  material_group_code    varchar(50)                   not null references projektivelho.material_group (code),
  material_category_code varchar(50)                   not null references projektivelho.material_category (code),
  assignment_oid         varchar(50)                   null references projektivelho.assignment (oid),
  project_oid            varchar(50)                   null references projektivelho.project (oid),
  project_group_oid      varchar(50)                   null references projektivelho.project_group (oid)
);
comment on table projektivelho.document is 'Handling status & metadata for Projektivelho documents';
select common.add_metadata_columns('projektivelho', 'document');
select common.add_table_versioning('projektivelho', 'document');


create table projektivelho.document_content
(
  document_id int primary key references projektivelho.document (id),
  content     xml not null
);
comment on table projektivelho.document_content
  is 'File contents for non-discarded documents, fetched from ProjektiVelho';

create table projektivelho.document_rejection
(
  id               int primary key generated always as identity,
  document_id      int          not null references projektivelho.document (id),
  document_version int          not null,
  reason           varchar(150) not null,

  constraint projektivelho_document_rejection_document_version_fkey
    foreign key (document_id, document_version) references projektivelho.document_version (id, version)
);
comment on table projektivelho.document_rejection is 'Rejection information for discarded ProjektiVelho documents';
