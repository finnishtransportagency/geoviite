create table geometry.plan_application
(
  id                  int primary key generated always as identity,
  name                varchar(100) not null,
  manufacturer        varchar(100) not null,
  application_version varchar(40)  not null,
  unique_name         varchar(141) not null unique generated always as
    (common.nospace_lowercase(name) || ' ' || common.nospace_lowercase(application_version)) stored
);

select common.add_table_metadata('geometry', 'plan_application');
comment on table geometry.plan_application is 'Plan Application: Software used to create the plan.';
