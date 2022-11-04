create table common.role_privilege
(
  role_code      varchar(10) not null references common.role (code),
  privilege_code varchar(10) not null references common.privilege (code),

  primary key (role_code, privilege_code)
);

select common.add_table_metadata('common', 'role_privilege');
comment on table common.role_privilege is 'Role-Privilege linking: assigns privileges to roles.';
