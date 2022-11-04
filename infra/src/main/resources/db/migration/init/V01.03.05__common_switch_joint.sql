create table common.switch_joint
(
  number              int                     not null,
  switch_structure_id int                     not null references common.switch_structure (id),
  location            postgis.geometry(point) not null,

  primary key (switch_structure_id, number)
);

select common.add_table_metadata('common', 'switch_joint');
comment on table common.switch_joint is 'Switch structure joint: a joint point of a common.switch_structure.';
