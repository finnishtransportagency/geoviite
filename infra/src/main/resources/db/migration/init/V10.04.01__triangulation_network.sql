create table common.triangle_corner_point
(
  id                int primary key generated always as identity,
  coord_id          varchar(50) unique,
  n60               decimal(12, 6)                not null,
  n2000             decimal(12, 6)                not null,
  point_original    postgis.geometry(point, 2393) null,
  point_transformed postgis.geometry(point, 3067) generated always as
    (
    postgis.st_transform(point_original, 3067)
    ) stored
);

create table common.triangulation_network
(
  id                  int primary key generated always as identity,
  coord1_id           varchar(50) references common.triangle_corner_point (coord_id),
  coord2_id           varchar(50) references common.triangle_corner_point (coord_id),
  coord3_id           varchar(50) references common.triangle_corner_point (coord_id),
  polygon_original    postgis.geometry(polygon, 2393) null,
  polygon_transformed postgis.geometry(polygon, 3067) generated always as
    (
    postgis.st_transform(polygon_original, 3067)
    ) stored
);

create index triangulation_network_polygon_transformed_idx
  on common.triangulation_network
    using gist (polygon_transformed);

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
