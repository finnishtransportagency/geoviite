create type geometry.element_type as enum ('LINE', 'CURVE', 'CLOTHOID', 'BIQUADRATIC_PARABOLA');
create table geometry.element
(
  alignment_id              int                       not null,
  element_index             int                       not null,
  oid_part                  varchar(10)               null,
  type                      geometry.element_type     not null,
  name                      varchar(100)              null,
  length                    decimal(13, 6)            not null,
  sta_start                 decimal(13, 6)            not null,
  start_point               postgis.geometry(point)   not null,
  end_point                 postgis.geometry(point)   not null,

  rotation                  common.rotation_direction null,

  curve_radius              decimal(12, 6)            null,
  curve_chord               decimal(12, 6)            null,
  curve_center_point        postgis.geometry(point)   null,

  spiral_dir_start          decimal(12, 6)            null,
  spiral_dir_end            decimal(12, 6)            null,
  spiral_radius_start       decimal(12, 6)            null,
  spiral_radius_end         decimal(12, 6)            null,
  spiral_pi_point           postgis.geometry(point)   null,

  clothoid_constant         decimal(12, 6)            null,

  switch_id                 int                       null,
  switch_start_joint_number int                       null,
  switch_end_joint_number   int                       null,

  primary key (alignment_id, element_index)
);

comment on table geometry.element is 'Geometry element: a mathematically defined piece of a geometry.alignment.';
