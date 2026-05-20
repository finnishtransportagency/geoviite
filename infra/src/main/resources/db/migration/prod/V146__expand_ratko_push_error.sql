alter table integrations.ratko_push_error
  add column ratko_response_code varchar(20)  null,
  add column technical_message   varchar(500) not null default '',
  add column track_number_oid    varchar(50)  null,
  add column location_track_oid  varchar(50)  null,
  add column switch_oid          varchar(50)  null;

alter table integrations.ratko_push_error
  alter column operation drop not null,
  alter column technical_message drop default;

update integrations.ratko_push_error
set
  track_number_oid = (
    select external_id
      from layout.track_number_external_id
      where id = track_number_id and layout_context_id = 'main_official'
  )
  where track_number_id is not null;

update integrations.ratko_push_error
set
  location_track_oid = (
    select external_id
      from layout.location_track_external_id
      where id = location_track_id and layout_context_id = 'main_official'
  )
  where location_track_id is not null;

update integrations.ratko_push_error
set
  switch_oid = (
    select external_id
      from layout.switch_external_id
      where id = switch_id and layout_context_id = 'main_official'
  )
  where switch_id is not null;
