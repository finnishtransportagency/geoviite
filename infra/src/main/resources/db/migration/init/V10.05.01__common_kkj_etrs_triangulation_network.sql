create table common.kkj_etrs_triangle_corner_point
(
  id         int primary key,
  coord_kkj  postgis.geometry(point, 2393) not null,
  coord_etrs postgis.geometry(point, 3067) not null
);

create table common.kkj_etrs_triangulation_network
(
  id        int primary key generated always as identity,
  coord1_id int             not null references common.kkj_etrs_triangle_corner_point (id),
  coord2_id int             not null references common.kkj_etrs_triangle_corner_point (id),
  coord3_id int             not null references common.kkj_etrs_triangle_corner_point (id),
  a1        decimal(17, 16) not null,
  a2        decimal(17, 16) not null,
  delta_e   decimal(12, 4)  not null,
  b1        decimal(17, 16) not null,
  b2        decimal(17, 16) not null,
  delta_n   decimal(7, 4)   not null
);
