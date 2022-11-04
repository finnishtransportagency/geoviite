create table common.switch_structure
(
  id                        int primary key generated always as identity,
  type                      varchar(50) not null unique,
  presentation_joint_number int         not null
);

select common.add_table_metadata('common', 'switch_structure');
comment on table common.switch_structure is 'Switch structure: Definition of shape for a switch type.';
