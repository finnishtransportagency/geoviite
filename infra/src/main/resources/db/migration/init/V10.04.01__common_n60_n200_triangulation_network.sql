create table common.n60_n2000_triangle_corner_point
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

create table common.n60_n2000_triangulation_network
(
  id                  int primary key generated always as identity,
  coord1_id           varchar(50) references common.n60_n2000_triangle_corner_point (coord_id),
  coord2_id           varchar(50) references common.n60_n2000_triangle_corner_point (coord_id),
  coord3_id           varchar(50) references common.n60_n2000_triangle_corner_point (coord_id),
  polygon_original    postgis.geometry(polygon, 2393) null,
  polygon_transformed postgis.geometry(polygon, 3067) generated always as
    (
    postgis.st_transform(polygon_original, 3067)
    ) stored
);

create index n60_n2000_triangulation_network_polygon_transformed_idx
  on common.n60_n2000_triangulation_network
    using gist (polygon_transformed);
