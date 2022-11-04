create table integrations.ratko_push_error
(
  id                int primary key generated always as identity,
  ratko_push_id     int                                     not null references integrations.ratko_push (id),
  track_number_id   int references layout.track_number (id),
  location_track_id int references layout.location_track (id),
  switch_id         int references layout.switch (id),
  error_type        integrations.ratko_push_error_type      not null,
  operation integrations.ratko_push_error_operation not null,
  response_body     text not null
);

comment on table integrations.ratko_push_error is 'Errors that occurred during attempted push to Ratko.';
