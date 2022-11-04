create table geometry.plan_project
(
  id          int primary key generated always as identity,
  name        varchar(100) not null,
  description varchar(100) null,
  unique_name varchar(100) not null unique generated always as (common.nospace_lowercase(name)) stored
);

select common.add_table_metadata('geometry', 'plan_project');
comment on table geometry.plan_project is 'Plan project: Grouping for plans.';
