alter table geometry.plan
  alter column plan_application_id drop not null;
alter table geometry.plan_version
  alter column plan_application_id drop not null;
