-- There should not be any meaningful bulk transfers created, remove old structure
alter table publication.split
  drop column bulk_transfer_id;
alter table publication.split
  drop column bulk_transfer_state;

alter table publication.split_version
  drop column bulk_transfer_id;
alter table publication.split_version
  drop column bulk_transfer_state;

drop type publication.bulk_transfer_state;

-- New structure
create type integrations.bulk_transfer_state as
  enum ('PENDING', 'CREATED', 'IN_PROGRESS', 'DONE', 'FAILED');

create table integrations.ratko_bulk_transfer
(
  split_id               int primary key                  not null references publication.split (id),
  state                  integrations.bulk_transfer_state not null,

  expedited_start        boolean                          not null,
  temporary_failure      boolean                          not null,

  ratko_bulk_transfer_id int,
  ratko_start_time       timestamptz,
  ratko_end_time         timestamptz,

  assets_total           int,
  assets_moved           int,

  trex_assets_total      int,
  trex_assets_remaining  int
);

comment on table publication.split is 'Ratko bulk transfer status for a split';

select common.add_metadata_columns('integrations', 'ratko_bulk_transfer');
select common.add_table_versioning('integrations', 'ratko_bulk_transfer');
