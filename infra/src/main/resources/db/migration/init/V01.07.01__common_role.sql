create table common.role
(
  code       varchar(10) primary key not null,
  name       varchar(30)             not null,
  user_group varchar(30)             null unique
);

select common.add_table_metadata('common', 'role');
comment on table common.role is 'Role: Grouping of privileges that can be assigned to a user.';
