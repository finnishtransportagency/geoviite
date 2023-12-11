alter table common.role
  alter column code type varchar(20);

alter table common.role_version
  alter column code type varchar(20);

alter table common.privilege
  alter column code type varchar(20);

alter table common.privilege_version
  alter column code type varchar(20);

alter table common.role_privilege
  alter column role_code type varchar(20),
  alter column privilege_code type varchar(20);

alter table common.role_privilege_version
  alter column role_code type varchar(20),
  alter column privilege_code type varchar(20);
