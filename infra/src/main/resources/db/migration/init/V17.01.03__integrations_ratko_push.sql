create table integrations.ratko_push
(
  id         int primary key generated always as identity,
  start_time timestamptz                    not null,
  end_time   timestamptz                    null,
  status     integrations.ratko_push_status not null
);

comment on table integrations.ratko_push is 'Contains all Ratko pushes.';
