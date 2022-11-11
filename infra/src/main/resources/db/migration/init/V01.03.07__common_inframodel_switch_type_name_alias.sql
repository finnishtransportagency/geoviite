create table common.inframodel_switch_type_name_alias
(
  id    int primary key generated always as identity,
  type  varchar(50) not null,
  alias varchar(50) not null unique
);

select common.add_table_metadata('common', 'inframodel_switch_type_name_alias');
comment on table common.switch_structure is 'Other names that we allow for switch structure names in inframodel imports.';
