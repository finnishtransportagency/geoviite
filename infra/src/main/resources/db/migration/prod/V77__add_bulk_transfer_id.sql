alter table publication.split
  add column bulk_transfer_id int null,
  add constraint bulk_transfer_id_unique unique (bulk_transfer_id);

alter table publication.split_version
  add column bulk_transfer_id int null;
