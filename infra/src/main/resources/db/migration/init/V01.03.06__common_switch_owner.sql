create table common.switch_owner
(
  id   int primary key generated always as identity,
  name varchar(100) not null
);

select common.add_table_metadata('common', 'switch_owner');
comment on table common.switch_joint is 'Switch owners';
