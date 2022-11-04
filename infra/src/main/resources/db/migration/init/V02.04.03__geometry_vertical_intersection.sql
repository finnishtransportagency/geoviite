create type geometry.vertical_intersection_type as enum ('POINT', 'CIRCULAR_CURVE');
create table geometry.vertical_intersection
(
  alignment_id       int                                 not null,
  intersection_index int                                 not null,
  type               geometry.vertical_intersection_type not null,
  description        varchar(100)                        not null,
  point              postgis.geometry(point)             not null,
  circular_radius    decimal(12, 6)                      null,
  circular_length    decimal(12, 6)                      null,

  primary key (alignment_id, intersection_index)
);

comment on table geometry.vertical_intersection is
  'Geometry vertical intersection: a point-value for profile (height) along an geometry.alignment.';
