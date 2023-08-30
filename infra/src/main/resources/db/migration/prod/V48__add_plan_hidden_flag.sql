alter table geometry.plan_version add column hidden boolean not null default false;
alter table geometry.plan add column hidden boolean not null default false;

alter table geometry.plan alter column hidden drop default;
alter table geometry.plan_version alter column hidden drop default;
