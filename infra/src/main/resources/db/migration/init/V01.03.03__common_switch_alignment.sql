create table common.switch_alignment
(
  id                  int primary key generated always as identity,
  switch_structure_id int   not null references common.switch_structure (id),
  joint_numbers       int[] not null
);

select common.add_table_metadata('common', 'switch_alignment');
comment on table common.switch_alignment is 'Switch structure alignment: a single line in a common.switch_structure.';
