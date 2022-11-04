create table integrations.lock
(
  name         varchar(64) not null primary key,
  locked_until timestamp   not null,
  locked_at    timestamp   not null
);

comment on table integrations.lock is 'Lock table for integrations.';
