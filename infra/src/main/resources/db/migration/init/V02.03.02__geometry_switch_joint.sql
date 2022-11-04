create table geometry.switch_joint
(
  switch_id int                     not null,
  number    int                     not null,
  location  postgis.geometry(point) not null,

  primary key (switch_id, number)
);

comment on table geometry.switch_joint is 'Geometry switch joint: named point of a geometry.switch.';
