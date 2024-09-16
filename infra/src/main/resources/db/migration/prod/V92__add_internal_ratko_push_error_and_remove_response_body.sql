alter table integrations.ratko_push_error
  drop column response_body;
alter type integrations.ratko_push_error_type
  add value 'INTERNAL';
