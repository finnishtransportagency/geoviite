create table integrations.projektivelho_file
(
  id          int primary key generated always as identity,
  filename    varchar(100) not null,
  content     xml,
  change_time timestamp with time zone not null
);

comment on table integrations.projektivelho_file is 'Contains all files fetched from Projektivelho.';
