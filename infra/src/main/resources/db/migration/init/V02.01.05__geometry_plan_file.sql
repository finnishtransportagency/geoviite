create table geometry.plan_file
(
  id          int primary key generated always as identity,
  plan_id     int          null,
  name        varchar(100) not null,
  content     xml          not null
);

comment on table geometry.plan is 'Original Geometry Plan file.';
