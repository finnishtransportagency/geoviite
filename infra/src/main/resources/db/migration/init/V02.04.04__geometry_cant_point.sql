create type geometry.cant_transition_type as enum ('LINEAR', 'BIQUADRATIC_PARABOLA');
create table geometry.cant_point
(
  alignment_id     int                           not null,
  cant_point_index int                           not null,
  station          decimal(13, 6)                not null,
  applied_cant     decimal(10, 6)                not null,
  curvature        common.rotation_direction     not null,
  transition_type  geometry.cant_transition_type not null,

  primary key (alignment_id, cant_point_index)
);

comment on table geometry.cant_point is 'Geometry cant point: a point-value for cant (tilt) along an geometry.alignment.';
