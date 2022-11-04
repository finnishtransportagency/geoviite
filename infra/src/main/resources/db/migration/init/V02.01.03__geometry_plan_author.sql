create table geometry.plan_author
(
  id                  int primary key generated always as identity,
  company_name        varchar(100) not null,
  unique_company_name varchar(100) not null unique generated always as (common.nospace_lowercase(company_name)) stored
);

select common.add_table_metadata('geometry', 'plan_author');
comment on table geometry.plan_author is 'Plan author.';
