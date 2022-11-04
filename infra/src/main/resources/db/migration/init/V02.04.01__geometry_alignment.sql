create type geometry.cant_rotation_point as enum ('LEFT', 'RIGHT', 'INSIDE_RAIL', 'CENTER');
create table geometry.alignment
(
  id                  int primary key generated always as identity,
  plan_id             int                          not null,
  track_number_id     int                          null,
  name                varchar(50)                  not null,
  description         varchar(100)                 null,
  state               geometry.plan_state          null,
  oid_part            varchar(10)                  null,
  sta_start           decimal(13, 6)               not null,
  profile_name        varchar(100)                 null,
  cant_name           varchar(100)                 null,
  cant_description    varchar(100)                 null,
  cant_gauge          decimal(6, 3)                null,
  cant_rotation_point geometry.cant_rotation_point null,
  feature_type_code   varchar(3)                   null
);

comment on table geometry.alignment is 'Geometry Alignment: Single continuous line in a geometry plan.';
