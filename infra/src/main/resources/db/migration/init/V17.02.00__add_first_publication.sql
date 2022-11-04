-- All data imports should be done. Add a publication row to store this special moment.
-- This publication is used to get the first moment when Geoviite and Ratko were kind of in-sync.
insert into integrations.ratko_push
  (start_time, end_time, status)
  values
    (now(), now(), 'SUCCESSFUL');
