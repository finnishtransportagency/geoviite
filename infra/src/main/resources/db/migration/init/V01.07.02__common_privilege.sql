create table common.privilege
(
  code        varchar(10) primary key not null,
  name        varchar(30)             not null,
  description varchar(100)            not null
);

select common.add_table_metadata('common', 'privilege');
comment on table common.privilege is 'Privilege: Base unit of user permission control.';
