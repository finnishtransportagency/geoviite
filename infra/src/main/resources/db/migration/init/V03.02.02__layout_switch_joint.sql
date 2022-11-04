create table layout.switch_joint
(
  switch_id         int                           not null,
  switch_version    int                           not null,
  number            int                           not null,
  location          postgis.geometry(point, 3067) not null,
  location_accuracy common.location_accuracy      null,

  primary key (switch_id, number)
);

select common.add_metadata_columns('layout', 'switch_joint');
comment on table layout.switch_joint is 'Layout switch joint: named point of a layout.switch.';
