alter table publication.split
  add column bulk_transfer_id int null,
  add constraint bulk_transfer_id_unique unique (bulk_transfer_id);

alter table publication.split_version
  add column bulk_transfer_id int null;

create unique index bulk_transfer_state_in_progress_unique
  on publication.split (bulk_transfer_state)
  where geoviite.publication.split.bulk_transfer_state = 'IN_PROGRESS';
