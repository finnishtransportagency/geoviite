alter table integrations.ratko_push_error
  alter column operation drop not null,
  add column ratko_response_code varchar(20)  null,
  add column technical_message   varchar(500) not null default '',
  alter column technical_message drop default;
