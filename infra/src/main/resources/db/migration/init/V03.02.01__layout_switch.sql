create table layout.switch
(
  id                  int                   primary key generated always as identity,
  external_id         varchar(50)           null,
  geometry_switch_id  int                   null,
  name                varchar(50)           not null,
  switch_structure_id int                   not null,
  state_category      layout.state_category not null,
  trap_point          boolean               null,
  owner_id            int                   null,
  draft               boolean               not null,
  draft_of_switch_id  int                   null,
  source              layout.geometry_source not null
);

select common.add_metadata_columns('layout', 'switch');
comment on table layout.switch is 'Layout switch.';
