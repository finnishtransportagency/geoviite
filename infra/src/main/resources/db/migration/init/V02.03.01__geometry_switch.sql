create table geometry.switch
(
  id                  int primary key generated always as identity,
  plan_id             int                 not null,
  name                varchar(50)         not null,
  switch_structure_id int                 null,
  type_name           varchar(30)         not null,
  state               geometry.plan_state null
);

comment on table geometry.switch is 'Geometry Switch.';
