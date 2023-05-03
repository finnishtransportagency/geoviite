create type integrations.projektivelho_file_status as enum ('NOT_IM', 'IMPORTED', 'REJECTED', 'ACCEPTED');
create type integrations.projektivelho_search_status as enum ('WAITING', 'FETCHING', 'FINISHED');

create table integrations.projektivelho_search
(
  id          int primary key generated always as identity,
  status      integrations.projektivelho_search_status not null,
  token       varchar(50),
  valid_until timestamp with time zone not null
);

create table integrations.projektivelho_file_content
(
  id          int primary key generated always as identity,
  filename    varchar(100) not null,
  content     xml not null
);

create table integrations.projektivelho_file
(
  id          int primary key generated always as identity,
  oid         varchar(50),
  file_id     int references integrations.projektivelho_file_content (id),
  change_time timestamp with time zone not null,
  status      integrations.projektivelho_file_status not null
);

comment on table integrations.projektivelho_file is 'Contains all files fetched from Projektivelho.';
