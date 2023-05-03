create type integrations.projektivelho_file_status as enum ('NOT_IM', 'IMPORTED', 'REJECTED', 'ACCEPTED');
create type integrations.projektivelho_search_status as enum ('WAITING', 'FETCHING', 'FINISHED');

create table integrations.projektivelho_search
(
  id          int primary key generated always as identity,
  status      integrations.projektivelho_search_status not null,
  token       varchar(50),
  valid_until timestamp with time zone not null
);

create table integrations.projektivelho_file_metadata
(
  id          int primary key generated always as identity,
  oid         varchar(50) not null,
  filename    varchar(100) not null,
  version     varchar(50) not null,
  description varchar(150),
  file_state  varchar(50),
  category    varchar(50),
  doc_type    varchar(50),
  asset_group varchar(50),
  change_time timestamp with time zone not null,
  status      integrations.projektivelho_file_status not null
);

create table integrations.projektivelho_file
(
  metadata_id int primary key references integrations.projektivelho_file_metadata(id),
  content     xml not null
);

comment on table integrations.projektivelho_search is 'Contains searches to ProjektiVelho.';
comment on table integrations.projektivelho_file_metadata is 'Contains metadata for all files fetched from Projektivelho.';
comment on table integrations.projektivelho_file is 'Contains all non-discarded files fetched from ProjektiVelho.';
