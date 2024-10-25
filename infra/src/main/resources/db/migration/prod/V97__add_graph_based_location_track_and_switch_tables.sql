-- set session geoviite.edit_user to 'MANUAL';
drop table if exists layout.location_track_2;
create table layout.location_track_2
(
  id                                 int primary key generated always as identity,
  alignment_id                       int                                        not null,
  alignment_version                  int                                        not null,
  track_number_id                    int                                        not null,
  external_id                        varchar(50)                                null,
  name                               varchar(50)                                not null,
  description                        varchar(256)                               not null,
  type                               layout.track_type                          null,
  state                              layout.state                               not null,
  draft                              boolean                                    not null,
  draft_of_location_track_id         int                                        null,
  duplicate_of_location_track_id     int                                        null,
  topological_connectivity           layout.track_topological_connectivity_type not null,
  topology_start_switch_id           int                                        null,
  topology_start_switch_joint_number int                                        null,
  topology_end_switch_id             int                                        null,
  topology_end_switch_joint_number   int                                        null
);
select common.add_metadata_columns('layout', 'location_track_2');
comment on table layout.location_track_2 is 'Layout Location Track: Single track of the layout network';
alter table layout.location_track_2 add constraint location_track_2_id_version_unique unique (id, version);

drop table if exists layout.location_track_2_edge;
create table layout.location_track_2_edge
(
  location_track_id      int not null,
  location_track_version int not null,
  edge_index             int not null,
  start_node_id          int not null references layout.node (id),
  end_node_id            int not null references layout.node (id),
  primary key (location_track_id, edge_index),
  constraint location_track_edge_location_track_fkey
    foreign key (location_track_id, location_track_version) references layout.location_track_2 (id, version),
  constraint location_track_edge_edge_fkey
    foreign key (start_node_id, end_node_id) references layout.edge (start_node_id, end_node_id)
);

drop table if exists layout.switch_2;
create table layout.switch_2
(
  id                  int primary key generated always as identity,
  external_id         varchar(50)            null,
  geometry_switch_id  int                    null,
  name                varchar(50)            not null,
  switch_structure_id int                    not null,
  state_category      layout.state_category  not null,
  trap_point          boolean                null,
  owner_id            int                    null,
  draft               boolean                not null,
  draft_of_switch_id  int                    null,
  source              layout.geometry_source not null
);
select common.add_metadata_columns('layout', 'switch_2');
comment on table layout.switch_2 is 'Layout switch.';
alter table layout.switch_2 add constraint switch_2_id_version_unique unique (id, version);

drop table if exists layout.switch_joint_version_2;
create table layout.switch_joint_version_2
(
  switch_id         int                           not null,
  switch_version    int                           not null,
  number            int                           not null,
  location          postgis.geometry(point, 3067) not null,
  location_accuracy common.location_accuracy      null,
  node_id           int                           not null references layout.node (id),
  primary key (switch_id, number),
  constraint switch_joint_switch_fkey
    foreign key (switch_id, switch_version) references layout.switch_2 (id, version)
);
